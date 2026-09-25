package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.Vec2
import kotlinx.coroutines.flow.Flow

/** Streams the plan, router, pins and room names Diagnose works on. */
class ObservePlanContext(private val repository: DiagnoseRepository) {
    operator fun invoke(): Flow<PlanContext> = repository.observePlanContext()
}

/** Applies the optimizer's suggestion: moves the router pin to [pos]. Throws on a storage failure. */
class MoveRouter(private val repository: DiagnoseRepository) {
    suspend operator fun invoke(pos: Vec2) = repository.moveRouter(pos)
}
