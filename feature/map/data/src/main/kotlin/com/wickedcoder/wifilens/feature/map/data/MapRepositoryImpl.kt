@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // flatMapLatest

package com.wickedcoder.wifilens.feature.map.data

import android.database.sqlite.SQLiteConstraintException
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.database.DevicePinEntity
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.GridPlanWithCells
import com.wickedcoder.wifilens.core.database.PinDao
import com.wickedcoder.wifilens.core.database.PlanDao
import com.wickedcoder.wifilens.core.database.RoomDao
import com.wickedcoder.wifilens.core.database.RoomEntity
import com.wickedcoder.wifilens.core.database.RouterPinEntity
import com.wickedcoder.wifilens.core.database.TransactionRunner
import com.wickedcoder.wifilens.core.database.toDomain
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
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
import javax.inject.Inject

/**
 * [MapRepository] backed by Room. Plan rows are always updated in place, never `insert(REPLACE)`d: REPLACE deletes
 * the old row first, and cells, rooms and pins cascade-delete on the plan's id, so a REPLACE autosave used to wipe
 * every pin. Content writes are addressed by plan id (ADR 0007), so a late autosave can't land on another plan.
 */
class MapRepositoryImpl
    @Inject
    constructor(
        private val gridPlanDao: GridPlanDao,
        private val planDao: PlanDao,
        private val roomDao: RoomDao,
        private val pinDao: PinDao,
        private val content: PlanContentStore,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : MapRepository {
        override fun getActivePlan(): Flow<GridPlan?> =
            gridPlanDao.getActivePlan().map { it?.toDomain() }

        override fun observePlan(): Flow<PlanSnapshot> =
            gridPlanDao.observeSnapshot().map { snapshot ->
                if (snapshot == null) {
                    PlanSnapshot(planId = null, name = null, plan = null, rooms = emptyList())
                } else {
                    PlanSnapshot(
                        planId = snapshot.plan.id,
                        name = snapshot.plan.name,
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

        // One transaction: cells and rooms commit together, so observers see a single consistent snapshot.
        override suspend fun savePlan(planId: Long, plan: GridPlan, rooms: List<Room>) = transactions.run {
            val existing = planDao.getPlan(planId) ?: return@run // deleted meanwhile: nothing to save into
            gridPlanDao.updatePlan(existing.copy(width = plan.width, height = plan.height, updatedAt = clock.nowMillis()))
            content.writeContent(planId, plan, rooms)
        }

        override suspend fun clearPlan() {
            val existing = gridPlanDao.getActivePlan().first()?.plan ?: return
            gridPlanDao.deletePlan(existing) // cascades to cells/rooms/pins via ForeignKey.CASCADE
        }

        override suspend fun setRouterPin(pos: Vec2, band: String) {
            val planId = requirePlanId()
            // One router constraint is enforced at the DB level (unique index on planId in RouterPinEntity); REPLACE
            // lets "move the router" overwrite the row, and a genuine violation becomes a domain exception here.
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
                ?: throw MapRepositoryException("No active plan; create a plan before placing pins")
    }

private fun RoomEntity.toDomain() = Room(id = roomId, name = name)
