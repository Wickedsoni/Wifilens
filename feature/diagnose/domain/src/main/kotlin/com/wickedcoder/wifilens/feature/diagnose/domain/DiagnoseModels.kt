package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Vec2

/** Everything Diagnose needs to know about the user's home at one moment. */
data class PlanContext(
    val plan: GridPlan?,
    val routerPos: Vec2?,
    val devicePins: List<DevicePin>,
    /** Room id to display name, for labelling the per-room summary. */
    val roomNames: Map<Int, String>,
)

/** One tile's predicted signal, the per-cell output of `predictRssi`. */
data class TileCoverage(val pos: Vec2, val rssi: Float)

/** A room's coverage rolled up to a single average, for the Coverage tab's room list. */
data class RoomSummary(val roomId: Int, val name: String, val avgRssi: Float)

enum class Severity { Poor, Fair }

/** Plain-English only, no tile coordinates. This is meant for a non-technical reader. */
data class Finding(val severity: Severity, val description: String)

/** The full result of predicting coverage for one [PlanContext]. */
data class CoverageReport(
    val coverage: List<TileCoverage>,
    /** The device with the weakest predicted signal, with that signal in dBm. Null when there are no device pins. */
    val worstDevice: Pair<DevicePin, Float>?,
    val roomSummaries: List<RoomSummary>,
    val findings: List<Finding>,
) {
    companion object {
        val Empty = CoverageReport(emptyList(), null, emptyList(), emptyList())
    }
}

/** Outcome of searching every walkable tile for the best router position. */
data class BestSpot(
    val bestTile: Vec2?,
    /** How many dB the worst device gains by moving the router to [bestTile]; null when no router is placed. */
    val gainDb: Float?,
    /** The router already sits on the best tile, so there is nothing to suggest. */
    val alreadyOptimal: Boolean,
    /** Every walkable tile's worst-case device signal, for shading the whole grid. */
    val scores: Map<Vec2, Float>,
)
