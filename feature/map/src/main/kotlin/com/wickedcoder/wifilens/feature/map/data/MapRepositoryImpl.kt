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
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Material
import com.wickedcoder.wifilens.core.rf.Vec2
import com.wickedcoder.wifilens.feature.map.domain.DevicePin
import com.wickedcoder.wifilens.feature.map.domain.MapRepository
import com.wickedcoder.wifilens.feature.map.domain.MapRepositoryException
import com.wickedcoder.wifilens.feature.map.domain.Room
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
) : MapRepository {

    override fun getActivePlan(): Flow<GridPlan?> =
        gridPlanDao.getActivePlan().map { it?.toDomain() }

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

    override suspend fun savePlan(plan: GridPlan, rooms: List<Room>) {
        val existingId = gridPlanDao.getActivePlan().first()?.plan?.id ?: 0L
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
        gridPlanDao.getActivePlan().first()?.plan?.id
            ?: throw MapRepositoryException("No active plan — create a plan before placing pins")
}

private fun GridPlanWithCells.toDomain(): GridPlan {
    val byPosition = cells.associateBy { it.x to it.y }
    val ordered = (0 until plan.height).flatMap { y ->
        (0 until plan.width).map { x ->
            byPosition[x to y]?.cellTypeJson ?: CellType.Empty(Material.Drywall)
        }
    }
    return GridPlan(width = plan.width, height = plan.height, cells = ordered)
}

private fun RoomEntity.toDomain() = Room(id = roomId, name = name)
