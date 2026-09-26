package com.wickedcoder.wifilens.feature.more.data

import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.feature.more.domain.FloorPlanRepository
import kotlinx.coroutines.flow.first

/** Room-backed [FloorPlanRepository]. Deleting the plan row cascades to its cells, rooms and pins. */
class FloorPlanRepositoryImpl(private val gridPlanDao: GridPlanDao) : FloorPlanRepository {
    override suspend fun deleteActivePlan() {
        gridPlanDao
            .getActivePlan()
            .first()
            ?.plan
            ?.let { gridPlanDao.deletePlan(it) }
    }
}
