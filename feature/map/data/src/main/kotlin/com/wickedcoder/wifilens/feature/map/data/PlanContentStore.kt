package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.database.CellEntity
import com.wickedcoder.wifilens.core.database.DevicePinEntity
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.GridPlanEntity
import com.wickedcoder.wifilens.core.database.GridPlanWithCells
import com.wickedcoder.wifilens.core.database.PinDao
import com.wickedcoder.wifilens.core.database.PlanDao
import com.wickedcoder.wifilens.core.database.RoomDao
import com.wickedcoder.wifilens.core.database.RoomEntity
import com.wickedcoder.wifilens.core.database.RouterPinEntity
import com.wickedcoder.wifilens.core.database.toDomain
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.map.domain.PlanArchive
import javax.inject.Inject

/** Band isn't part of the plan file (the router pin's band is a scan-time detail); imported routers use 5 GHz. */
private const val IMPORTED_ROUTER_BAND = "5"

/**
 * Row-level reads and writes of a plan's content, shared by [MapRepositoryImpl] (editing the open plan) and
 * [PlanRepositoryImpl] (creating, duplicating, importing, exporting). Callers own the transaction.
 */
class PlanContentStore
    @Inject
    constructor(
        private val gridPlanDao: GridPlanDao,
        private val planDao: PlanDao,
        private val roomDao: RoomDao,
        private val pinDao: PinDao,
        private val clock: Clock,
    ) {
        /** Grid cells (keyed planId/x/y, so REPLACE overwrites in place) and the room list of plan [planId]. */
        suspend fun writeContent(planId: Long, plan: GridPlan, rooms: List<Room>) {
            val cells = plan.cells.mapIndexed { index, cellType ->
                CellEntity(planId = planId, x = index % plan.width, y = index / plan.width, cellTypeJson = cellType)
            }
            gridPlanDao.insertCells(cells)
            roomDao.replaceRooms(planId, rooms.map { RoomEntity(planId = planId, roomId = it.id, name = it.name) })
        }

        /** Inserts a new plan row, opened now, plus its content. Returns the new id. */
        suspend fun insertPlan(name: String, plan: GridPlan, rooms: List<Room>): Long {
            val now = clock.nowMillis()
            val planId = gridPlanDao.insertPlan(
                GridPlanEntity(name = name, width = plan.width, height = plan.height, createdAt = now, updatedAt = now, lastOpenedAt = now),
            )
            writeContent(planId, plan, rooms)
            return planId
        }

        /** Inserts [archive] as a new, opened plan with its pins. Returns the new id. */
        suspend fun insertArchive(archive: PlanArchive): Long {
            val planId = insertPlan(archive.name, archive.plan, archive.rooms)
            archive.router?.let {
                pinDao.insertRouterPin(RouterPinEntity(planId = planId, x = it.x, y = it.y, band = IMPORTED_ROUTER_BAND))
            }
            archive.devices.forEach {
                pinDao.insertDevicePin(DevicePinEntity(planId = planId, x = it.pos.x, y = it.pos.y, name = it.name))
            }
            return planId
        }

        /** Everything the user drew in plan [planId], or null if it doesn't exist. */
        suspend fun readArchive(planId: Long): PlanArchive? {
            val snapshot = planDao.getSnapshot(planId) ?: return null
            return PlanArchive(
                name = snapshot.plan.name,
                plan = GridPlanWithCells(snapshot.plan, snapshot.cells).toDomain(),
                rooms = snapshot.rooms.sortedBy { it.roomId }.map { Room(id = it.roomId, name = it.name) },
                router = pinDao.routerPin(planId)?.let { Vec2(it.x, it.y) },
                devices = pinDao.devicePins(planId).map { DevicePin(Vec2(it.x, it.y), it.name) },
            )
        }
    }
