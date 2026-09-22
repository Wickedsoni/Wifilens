package com.wickedcoder.wifilens.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A plan row joined with all of its cells — what [GridPlanDao.getActivePlan] actually returns,
 * since Room can't embed a one-to-many relation directly into a query's row shape. */
data class GridPlanWithCells(
    @Embedded val plan: GridPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId") val cells: List<CellEntity>,
)

/** CRUD for the plan row and its cells. See [MapRepositoryImpl][com.wickedcoder.wifilens.feature.map.data.MapRepositoryImpl]
 * for why [updatePlan] is used on every autosave instead of re-inserting. */
@Dao
interface GridPlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: GridPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: GridPlanEntity)

    @Delete
    suspend fun deletePlan(plan: GridPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<CellEntity>)

    /** Hard constraint: one saved plan — this project only ever has a single row here. */
    @Transaction
    @Query("SELECT * FROM grid_plan LIMIT 1")
    fun getActivePlan(): Flow<GridPlanWithCells?>
}
