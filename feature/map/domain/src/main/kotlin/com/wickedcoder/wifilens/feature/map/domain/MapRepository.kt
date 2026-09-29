package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.PlanCalibration
import com.wickedcoder.wifilens.core.model.Room
import com.wickedcoder.wifilens.core.model.Vec2
import kotlinx.coroutines.flow.Flow

/** Wraps a persistence failure (e.g. a DB constraint violation) as a domain-level error the
 * presentation layer can show to the user, instead of leaking a Room/SQLite exception type. */
class MapRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The active plan and its rooms as one consistent read. [plan] is null when no plan exists yet. [calibration] is the
 * path-loss model fitted from this plan's walk survey, if any.
 */
data class PlanSnapshot(
    val planId: Long?,
    val name: String?,
    val plan: GridPlan?,
    val rooms: List<Room>,
    val calibration: PlanCalibration? = null,
)

/**
 * The open (active) plan's content: the grid, its rooms and the router/device pins. Content writes are addressed by
 * plan id, so a delayed autosave can never land on a different plan after the user switches. Managing the set of
 * plans is [PlanRepository]'s job.
 */
interface MapRepository {
    /** Not suspend — Flow is already the async wrapper here. */
    fun getActivePlan(): Flow<GridPlan?>

    /** Plan + rooms in one emission. Prefer this over combining [getActivePlan] and [getRooms], which can
     * transiently pair a new plan with the previous room list right after a save. */
    fun observePlan(): Flow<PlanSnapshot>

    /**
     * Beyond what Day 2 spec'd: MapState needs rooms/router/device pins to render, and the spec's
     * interface only had write methods for those. Reading them back is not optional.
     */
    fun getRooms(): Flow<List<Room>>

    fun getRouterPin(): Flow<Vec2?>

    fun getDevicePins(): Flow<List<DevicePin>>

    /** Updates plan [planId]'s grid and rooms in place (never replace: pins cascade on the plan row). Keeps its name. */
    suspend fun savePlan(planId: Long, plan: GridPlan, rooms: List<Room>)

    /** Deletes the active plan; the next most recently opened plan becomes active. */
    suspend fun clearPlan()

    suspend fun setRouterPin(pos: Vec2, band: String)

    suspend fun addDevicePin(pos: Vec2, name: String)

    suspend fun removeDevicePin(pos: Vec2)
}
