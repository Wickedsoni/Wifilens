package com.wickedcoder.wifilens.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Hard constraint: one saved plan (see DEVELOPMENT.md) — in practice this table holds at most one row. */
@Entity(tableName = "grid_plan")
data class GridPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val width: Int,
    val height: Int,
)
