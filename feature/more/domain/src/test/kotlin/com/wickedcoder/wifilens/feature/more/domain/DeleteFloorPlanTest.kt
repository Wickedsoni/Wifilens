package com.wickedcoder.wifilens.feature.more.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteFloorPlanTest {
    private class FakeRepository : FloorPlanRepository {
        var deleted = 0
        var failure: Throwable? = null

        override suspend fun deleteActivePlan() {
            failure?.let { throw it }
            deleted++
        }
    }

    @Test
    fun `invoking deletes the active plan once`() = runTest {
        val repository = FakeRepository()

        DeleteFloorPlan(repository)()

        assertEquals(1, repository.deleted)
    }

    @Test
    fun `a storage failure reaches the caller`() = runTest {
        val repository = FakeRepository().apply { failure = IllegalStateException("disk full") }

        val error = runCatching { DeleteFloorPlan(repository)() }.exceptionOrNull()

        assertEquals("disk full", error?.message)
    }
}
