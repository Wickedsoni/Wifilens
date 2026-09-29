package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.model.GridPlan
import kotlinx.coroutines.flow.Flow

/** One row of the plans list. [isActive] marks the plan currently open on the Map. */
data class PlanSummary(val id: Long, val name: String, val width: Int, val height: Int, val updatedAt: Long, val isActive: Boolean)

/**
 * Managing the set of saved plans (ADR 0007): list, create, open, rename, duplicate, delete, export and import.
 * Editing the open plan's content is [MapRepository]'s job.
 */
interface PlanRepository {
    /** Every plan, most recently opened first; the first one is active. */
    fun observePlans(): Flow<List<PlanSummary>>

    /** Creates a new plan and opens it. Returns its id. */
    suspend fun createPlan(name: String, plan: GridPlan): Long

    suspend fun openPlan(planId: Long)

    suspend fun renamePlan(planId: Long, name: String)

    /** Copies grid, rooms and pins into a new plan named [newName] and opens it. Returns the new id. */
    suspend fun duplicatePlan(planId: Long, newName: String): Long

    /** Deletes a plan; if it was active, the next most recently opened plan becomes active. */
    suspend fun deletePlan(planId: Long)

    /** Writes plan [planId] as JSON to the document at [destinationUri] (Storage Access Framework URI). */
    suspend fun exportPlan(planId: Long, destinationUri: String)

    /**
     * Reads, validates and imports the document at [sourceUri] as a new, opened plan. Returns its id.
     * @throws PlanImportException when the file isn't a valid WifiLens plan; nothing is written then.
     */
    suspend fun importPlan(sourceUri: String): Long
}
