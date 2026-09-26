@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // flatMapLatest

package com.wickedcoder.wifilens.feature.map.data

import android.database.sqlite.SQLiteConstraintException
import com.wickedcoder.wifilens.core.database.CellEntity
import com.wickedcoder.wifilens.core.database.DevicePinEntity
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.GridPlanEntity
import com.wickedcoder.wifilens.core.database.GridPlanWithCells
import com.wickedcoder.wifilens.core.database.PinDao
import com.wickedcoder.wifilens.core.database.RoomDao
import com.wickedcoder.wifilens.core.database.RoomEntity
import com.wickedcoder.wifilens.core.database.RouterPinEntity
import com.wickedcoder.wifilens.core.database.TransactionRunner
import com.wickedcoder.wifilens.core.database.toDomain
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import com.wickedcoder.wifilens.feature.map.domain.MapRepositoryException
import com.wickedcoder.wifilens.feature.map.domain.PlanSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * [MapRepository] backed by Room. The one non-obvious rule here is in [savePlan]: it updates the
 * existing plan row in place rather than replacing it, because `insert(REPLACE)` deletes the old
 * row first, and the pin/room/cell tables all cascade-delete on the plan's id — an autosave using
 * REPLACE used to silently wipe the router pin and every device pin on every keystroke of painting.
 */
class MapRepositoryImpl(
    private val gridPlanDao: GridPlanDao,
    private val roomDao: RoomDao,
    private val pinDao: PinDao,
    private val transactions: TransactionRunner,
) : MapRepository {
    override fun getActivePlan(): Flow<GridPlan?> =
        gridPlanDao.getActivePlan().map { it?.toDomain() }

    override fun observePlan(): Flow<PlanSnapshot> =
        gridPlanDao.observeSnapshot().map { snapshot ->
            if (snapshot == null) {
                PlanSnapshot(plan = null, rooms = emptyList())
            } else {
                PlanSnapshot(
                    plan = GridPlanWithCells(snapshot.plan, snapshot.cells).toDomain(),
                    rooms = snapshot.rooms.sortedBy { it.roomId }.map { it.toDomain() },
                )
            }
        }

    override fun getRooms(): Flow<List<Room>> =
        gridPlanDao.getActivePlan().flatMapLatest { planWithCells ->
            val planId = planWithCells?.plan?.id ?: return@flatMapLatest flowOf(emptyList())
            roomDao.getRoomsForPlan(planId).map { rooms -> rooms.map { it.toDomain() } }
        }

    override fun getRouterPin(): Flow<Vec2?> =
        gridPlanDao.getActivePlan().flatMapLatest { planWithCells ->
            val planId = planWithCells?.plan?.id ?: return@flatMapLatest flowOf(null)
            pinDao.observeRouterPin(planId).map { pin -> pin?.let { Vec2(it.x, it.y) } }
        }

    override fun getDevicePins(): Flow<List<DevicePin>> =
        gridPlanDao.getActivePlan().flatMapLatest { planWithCells ->
            val planId = planWithCells?.plan?.id ?: return@flatMapLatest flowOf(emptyList())
            pinDao.observeDevicePins(planId).map { pins -> pins.map { DevicePin(Vec2(it.x, it.y), it.name) } }
        }

    // One transaction: cells and rooms commit together, so observers see a single consistent
    // snapshot. Written separately, the plan flow re-emitted with the OLD room list mid-save and
    // that stale list could overwrite a just-created room in the ViewModel.
    override suspend fun savePlan(plan: GridPlan, rooms: List<Room>) = transactions.run {
        val existingId = gridPlanDao
            .getActivePlan()
            .first()
            ?.plan
            ?.id ?: 0L
        val entity = GridPlanEntity(id = existingId, name = "Home", width = plan.width, height = plan.height)
        // Update in place, never insert(REPLACE): REPLACE deletes the old plan row first, and the
        // pin/room/cell tables all cascade-delete on it — so every autosave used to silently wipe
        // the router pin and every device pin.
        val planId = if (existingId == 0L) {
            gridPlanDao.insertPlan(entity)
        } else {
            gridPlanDao.updatePlan(entity)
            existingId
        }
        val cellEntities = plan.cells.mapIndexed { index, cellType ->
            CellEntity(planId = planId, x = index % plan.width, y = index / plan.width, cellTypeJson = cellType)
        }
        gridPlanDao.insertCells(cellEntities) // keyed (planId, x, y): REPLACE overwrites in place
        roomDao.replaceRooms(planId, rooms.map { RoomEntity(planId = planId, roomId = it.id, name = it.name) })
    }

    override suspend fun clearPlan() {
        val existing = gridPlanDao.getActivePlan().first()?.plan ?: return
        gridPlanDao.deletePlan(existing) // cascades to cells/rooms/pins via ForeignKey.CASCADE
    }

    override suspend fun setRouterPin(pos: Vec2, band: String) {
        val planId = requirePlanId()
        // One router constraint is enforced at the DB level (unique index on planId in
        // RouterPinEntity) — REPLACE lets a legitimate "move the router" overwrite the existing
        // row without a separate existence check in Kotlin; a genuine constraint violation still
        // surfaces as SQLiteConstraintException and is turned into a domain exception here.
        try {
            pinDao.insertRouterPin(RouterPinEntity(planId = planId, x = pos.x, y = pos.y, band = band))
        } catch (e: SQLiteConstraintException) {
            throw MapRepositoryException("Could not place router pin", e)
        }
    }

    override suspend fun addDevicePin(pos: Vec2, name: String) {
        val planId = requirePlanId()
        pinDao.insertDevicePin(DevicePinEntity(planId = planId, x = pos.x, y = pos.y, name = name))
    }

    override suspend fun removeDevicePin(pos: Vec2) {
        val planId = requirePlanId()
        val existing = pinDao.observeDevicePins(planId).first().find { it.x == pos.x && it.y == pos.y } ?: return
        pinDao.deleteDevicePin(existing)
    }

    private suspend fun requirePlanId(): Long =
        gridPlanDao
            .getActivePlan()
            .first()
            ?.plan
            ?.id
            ?: throw MapRepositoryException("No active plan — create a plan before placing pins")
}

private fun RoomEntity.toDomain() = Room(id = roomId, name = name)
