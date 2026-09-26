package com.wickedcoder.wifilens.feature.more.data

import com.wickedcoder.wifilens.core.database.CellEntity
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.GridPlanEntity
import com.wickedcoder.wifilens.core.database.GridPlanSnapshot
import com.wickedcoder.wifilens.core.database.GridPlanWithCells
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorPlanRepositoryImplTest {
    private val dao = FakeGridPlanDao()
    private val repository = FloorPlanRepositoryImpl(dao)

    @Test
    fun `deleting removes the active plan row`() = runTest {
        val plan = GridPlanEntity(id = 7, name = "Home", width = 5, height = 5)
        dao.plan.value = GridPlanWithCells(plan, emptyList())

        repository.deleteActivePlan()

        assertEquals(listOf(plan), dao.deleted)
    }

    @Test
    fun `deleting with no plan does nothing`() = runTest {
        repository.deleteActivePlan()

        assertTrue(dao.deleted.isEmpty())
    }
}

private class FakeGridPlanDao : GridPlanDao {
    val plan = MutableStateFlow<GridPlanWithCells?>(null)
    val deleted = mutableListOf<GridPlanEntity>()

    override suspend fun insertPlan(plan: GridPlanEntity): Long = plan.id

    override suspend fun updatePlan(plan: GridPlanEntity) = Unit

    override suspend fun deletePlan(plan: GridPlanEntity) {
        deleted += plan
    }

    override suspend fun insertCells(cells: List<CellEntity>) = Unit

    override fun getActivePlan(): Flow<GridPlanWithCells?> = plan

    override fun observeSnapshot(): Flow<GridPlanSnapshot?> = flowOf(null)
}
