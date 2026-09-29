package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.database.PlanDao
import com.wickedcoder.wifilens.core.database.TransactionRunner
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.feature.map.domain.MapRepositoryException
import com.wickedcoder.wifilens.feature.map.domain.PlanRepository
import com.wickedcoder.wifilens.feature.map.domain.PlanSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** [PlanRepository] backed by Room (ADR 0007). Multi-row operations (duplicate, import) run in one transaction. */
class PlanRepositoryImpl
    @Inject
    constructor(
        private val planDao: PlanDao,
        private val content: PlanContentStore,
        private val transactions: TransactionRunner,
        private val documents: PlanDocumentStore,
        private val clock: Clock,
    ) : PlanRepository {
        override fun observePlans(): Flow<List<PlanSummary>> =
            planDao.observePlans().map { plans ->
                plans.mapIndexed { index, plan ->
                    PlanSummary(plan.id, plan.name, plan.width, plan.height, plan.updatedAt, isActive = index == 0)
                }
            }

        override suspend fun createPlan(name: String, plan: GridPlan): Long = transactions.run {
            content.insertPlan(name, plan, rooms = emptyList())
        }

        override suspend fun openPlan(planId: Long) = planDao.markOpened(planId, clock.nowMillis())

        override suspend fun renamePlan(planId: Long, name: String) = planDao.rename(planId, name.trim(), clock.nowMillis())

        override suspend fun duplicatePlan(planId: Long, newName: String): Long = transactions.run {
            val archive = content.readArchive(planId) ?: throw MapRepositoryException("Plan $planId not found")
            content.insertArchive(archive.copy(name = newName))
        }

        override suspend fun deletePlan(planId: Long) = planDao.deletePlanById(planId) // cascades to its content

        override suspend fun exportPlan(planId: Long, destinationUri: String) {
            val archive = content.readArchive(planId) ?: throw MapRepositoryException("Plan $planId not found")
            documents.write(destinationUri, PlanArchiveCodec.encode(archive, exportedAt = clock.nowMillis()))
        }

        override suspend fun importPlan(sourceUri: String): Long {
            val archive = PlanArchiveCodec.decode(documents.read(sourceUri)) // validated before anything is written
            return transactions.run { content.insertArchive(archive) }
        }
    }
