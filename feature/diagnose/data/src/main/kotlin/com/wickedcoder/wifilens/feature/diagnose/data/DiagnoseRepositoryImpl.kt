@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // flatMapLatest

package com.wickedcoder.wifilens.feature.diagnose.data

import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.PinDao
import com.wickedcoder.wifilens.core.database.RoomDao
import com.wickedcoder.wifilens.core.database.RouterPinEntity
import com.wickedcoder.wifilens.core.database.toDomain
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.diagnose.domain.DiagnoseRepository
import com.wickedcoder.wifilens.feature.diagnose.domain.PlanContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Router band assumed when the plan has no router pin row yet (matches what Map writes). */
private const val DEFAULT_ROUTER_BAND = "5"

/**
 * Room-backed [DiagnoseRepository]. Reads the same tables Map writes, and writes back only the router pin
 * when the user applies the optimizer's suggestion.
 *
 * Pins and rooms are combined *inside* the restart triggered by a new plan, so every emission pairs a
 * plan with pins and rooms read for that same plan (see bug-log B-25 for why two independent flows are
 * not safe here).
 */
class DiagnoseRepositoryImpl(
    private val gridPlanDao: GridPlanDao,
    private val pinDao: PinDao,
    private val roomDao: RoomDao,
) : DiagnoseRepository {
    override fun observePlanContext(): Flow<PlanContext> =
        gridPlanDao.getActivePlan().flatMapLatest { planWithCells ->
            val planId = planWithCells?.plan?.id
            if (planWithCells == null || planId == null) {
                flowOf(PlanContext(plan = null, routerPos = null, devicePins = emptyList(), roomNames = emptyMap()))
            } else {
                combine(
                    pinDao.observeRouterPin(planId),
                    pinDao.observeDevicePins(planId),
                    roomDao.getRoomsForPlan(planId),
                ) { router, devices, rooms ->
                    PlanContext(
                        plan = planWithCells.toDomain(),
                        routerPos = router?.let { Vec2(it.x, it.y) },
                        devicePins = devices.map { DevicePin(Vec2(it.x, it.y), it.name) },
                        roomNames = rooms.associate { it.roomId to it.name },
                    )
                }
            }
        }

    override suspend fun moveRouter(pos: Vec2) {
        val planId = gridPlanDao
            .getActivePlan()
            .first()
            ?.plan
            ?.id ?: return
        val band = pinDao.observeRouterPin(planId).first()?.band ?: DEFAULT_ROUTER_BAND
        pinDao.insertRouterPin(RouterPinEntity(planId = planId, x = pos.x, y = pos.y, band = band))
    }
}
