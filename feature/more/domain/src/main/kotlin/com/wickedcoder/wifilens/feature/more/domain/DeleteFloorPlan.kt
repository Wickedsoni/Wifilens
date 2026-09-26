package com.wickedcoder.wifilens.feature.more.domain

/** Settings' destructive "delete floor plan" action. Throws on a storage failure; callers decide how to report it. */
class DeleteFloorPlan(private val repository: FloorPlanRepository) {
    suspend operator fun invoke() = repository.deleteActivePlan()
}
