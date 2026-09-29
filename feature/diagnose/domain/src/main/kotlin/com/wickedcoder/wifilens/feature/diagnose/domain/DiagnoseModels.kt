package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.PlanCalibration
import com.wickedcoder.wifilens.core.model.Vec2

/** Everything Diagnose needs to know about the user's home at one moment. */
data class PlanContext(
    val plan: GridPlan?,
    val routerPos: Vec2?,
    val devicePins: List<DevicePin>,
    /** Room id to display name, for labelling the per-room summary. */
    val roomNames: Map<Int, String>,
    /** Path-loss model fitted from this plan's walk survey; when set it replaces the Settings values. */
    val calibration: PlanCalibration? = null,
)

/** One tile's predicted signal, the per-cell output of `predictRssi`. */
data class TileCoverage(val pos: Vec2, val rssi: Float)

/**
 * A room's coverage rolled up to a single average, for the Coverage tab's room list. [name] is null for a room
 * without a name; the UI shows "Room <id>".
 */
data class RoomSummary(val roomId: Int, val name: String?, val avgRssi: Float)

enum class Severity { Poor, Fair }

/** What a [Finding] is about. The UI turns it into a plain-language sentence (no tile coordinates). */
enum class FindingKind { RoomWeak, RoomBorderline, DeviceBehindWalls, DeviceFar }

/** One coverage problem. [subject] is the room/device name (null: unnamed room [roomId]); the UI phrases it. */
data class Finding(
    val severity: Severity,
    val kind: FindingKind,
    val subject: String?,
    val roomId: Int? = null,
    val wallCount: Int = 0,
)

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
