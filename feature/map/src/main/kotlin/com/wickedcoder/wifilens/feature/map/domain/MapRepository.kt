package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Vec2
import kotlinx.coroutines.flow.Flow

/** Wraps a persistence failure (e.g. a DB constraint violation) as a domain-level error the
 * presentation layer can show to the user, instead of leaking a Room/SQLite exception type. */
class MapRepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Persistence for the single floor plan: the grid itself, its rooms, and the router/device pins. */
interface MapRepository {
    /** Not suspend — Flow is already the async wrapper here. */
    fun getActivePlan(): Flow<GridPlan?>

    /**
     * Beyond what Day 2 spec'd: MapState needs rooms/router/device pins to render, and the spec's
     * interface only had write methods for those. Reading them back is not optional.
     */
    fun getRooms(): Flow<List<Room>>
    fun getRouterPin(): Flow<Vec2?>
    fun getDevicePins(): Flow<List<DevicePin>>

    /** Upserts the plan and its rooms. Implementations must update the existing plan row rather
     * than replace it — see [com.wickedcoder.wifilens.feature.map.data.MapRepositoryImpl]. */
    suspend fun savePlan(plan: GridPlan, rooms: List<Room>)
    suspend fun clearPlan()
    suspend fun setRouterPin(pos: Vec2, band: String)
    suspend fun addDevicePin(pos: Vec2, name: String)
    suspend fun removeDevicePin(pos: Vec2)
}
