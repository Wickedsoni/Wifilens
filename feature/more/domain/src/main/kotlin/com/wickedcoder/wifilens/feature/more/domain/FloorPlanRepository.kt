package com.wickedcoder.wifilens.feature.more.domain

/** The one thing Settings needs from the stored floor plan: deleting it. Implemented on Room in `:feature:more:data`. */
interface FloorPlanRepository {
    /** Deletes the active plan with its rooms, pins and cells. Does nothing when there is no plan. */
    suspend fun deleteActivePlan()
}
