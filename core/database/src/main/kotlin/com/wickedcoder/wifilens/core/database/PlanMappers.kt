package com.wickedcoder.wifilens.core.database

import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.PlanCalibration

/**
 * Rebuilds the in-memory [GridPlan] from its stored rows. Cells are stored one row per tile, so this
 * lays them out row-major and fills any missing tile with drywall. The one place this mapping lives:
 * both the Map repository and Diagnose read plans through it.
 */
fun GridPlanWithCells.toDomain(): GridPlan {
    val byPosition = cells.associateBy { it.x to it.y }
    val ordered = (0 until plan.height).flatMap { y ->
        (0 until plan.width).map { x -> byPosition[x to y]?.cellTypeJson ?: CellType.Empty(Material.Drywall) }
    }
    return GridPlan(width = plan.width, height = plan.height, cells = ordered)
}

/** The stored calibration, when set (the columns are written together). */
fun GridPlanEntity.calibration(): PlanCalibration? {
    val reference = calibrationReferenceRssi ?: return null
    val exponent = calibrationPathLossExponent ?: return null
    return PlanCalibration(reference.toFloat(), exponent.toFloat(), (calibrationRmseDb ?: 0.0).toFloat())
}
