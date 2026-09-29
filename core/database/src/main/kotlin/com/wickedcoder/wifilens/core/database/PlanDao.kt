package com.wickedcoder.wifilens.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The set of saved plans (ADR 0007): listing, lookup by id, opening, renaming and deleting. */
@Dao
interface PlanDao {
    /** Every plan, most recently opened first (the first row is the active plan). */
    @Query("SELECT * FROM grid_plan ORDER BY lastOpenedAt DESC, id DESC")
    fun observePlans(): Flow<List<GridPlanEntity>>

    @Query("SELECT * FROM grid_plan WHERE id = :planId")
    suspend fun getPlan(planId: Long): GridPlanEntity?

    @Transaction
    @Query("SELECT * FROM grid_plan WHERE id = :planId")
    suspend fun getSnapshot(planId: Long): GridPlanSnapshot?

    @Query("UPDATE grid_plan SET lastOpenedAt = :now WHERE id = :planId")
    suspend fun markOpened(planId: Long, now: Long)

    @Query("UPDATE grid_plan SET name = :name, updatedAt = :now WHERE id = :planId")
    suspend fun rename(planId: Long, name: String, now: Long)

    /** Stores (or with nulls, clears) a plan's fitted prediction model. */
    @Query(
        "UPDATE grid_plan SET calibrationReferenceRssi = :referenceRssi, calibrationPathLossExponent = :exponent, " +
            "calibrationRmseDb = :rmseDb WHERE id = :planId",
    )
    suspend fun setCalibration(planId: Long, referenceRssi: Double?, exponent: Double?, rmseDb: Double?)

    @Query("DELETE FROM grid_plan WHERE id = :planId")
    suspend fun deletePlanById(planId: Long)
}
