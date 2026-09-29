package com.wickedcoder.wifilens.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved floor plan (v2: any number of them). The **active** plan is the most recently opened one
 * ([lastOpenedAt], ties broken by id), so "which plan is open" lives atomically with the data and can never point
 * at a deleted plan (ADR 0007). Timestamps are epoch millis; v1 rows migrate with 0. Calibration is filled by the
 * walk-survey fit (Sprint 7); null means the global prediction settings apply.
 */
@Entity(tableName = "grid_plan")
data class GridPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val lastOpenedAt: Long = 0,
    val calibrationReferenceRssi: Double? = null,
    val calibrationPathLossExponent: Double? = null,
    val calibrationRmseDb: Double? = null,
)
