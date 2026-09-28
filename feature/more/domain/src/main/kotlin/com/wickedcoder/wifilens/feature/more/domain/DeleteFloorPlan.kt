package com.wickedcoder.wifilens.feature.more.domain

import javax.inject.Inject

/** Settings' destructive "delete floor plan" action. Throws on a storage failure; callers decide how to report it. */
class DeleteFloorPlan
    @Inject
    constructor(private val repository: FloorPlanRepository) {
        suspend operator fun invoke() = repository.deleteActivePlan()
    }
