package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.Vec2
import kotlinx.coroutines.flow.Flow

/** Diagnose's view of the stored floor plan. Implemented in `:feature:diagnose:data` on top of Room. */
interface DiagnoseRepository {
    /**
     * The plan, router, device pins and room names as one consistent value, re-emitted whenever any of
     * them changes. When no plan exists it emits a [PlanContext] with a null plan.
     */
    fun observePlanContext(): Flow<PlanContext>

    /**
     * Moves the router pin to [pos], keeping its band. Does nothing when there is no plan.
     * Throws on a storage failure; callers decide how to report it.
     */
    suspend fun moveRouter(pos: Vec2)
}
