package com.wickedcoder.wifilens.feature.diagnose.presentation

import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Vec2
import com.wickedcoder.wifilens.feature.map.domain.DevicePin

/** Which of the two Diagnose sub-screens is showing. */
sealed interface DiagnoseTab {
    data object Coverage : DiagnoseTab
    data object BestSpot : DiagnoseTab
    data object Speed : DiagnoseTab
}

/** One tile's predicted signal — the per-cell output of [com.wickedcoder.wifilens.core.rf.predictRssi]. */
data class TileCoverage(val pos: Vec2, val rssi: Float)

/** A room's coverage rolled up to a single average, for the Coverage tab's room list. */
data class RoomSummary(val roomId: Int, val name: String, val avgRssi: Float)

enum class Severity { Poor, Fair }

/** Plain-English only — no tile coordinates. This is meant for a non-technical reader. */
data class Finding(val severity: Severity, val description: String)

/** Progress of the router-placement optimizer's tile-by-tile search. */
sealed interface OptimizerState {
    data object Idle : OptimizerState
    data class Running(val progress: Float) : OptimizerState // 0..1
    data object Complete : OptimizerState
    data object AlreadyOptimal : OptimizerState
}

/** Progress of the measured download speed test on the Speed tab. */
sealed interface SpeedTestState {
    data object Idle : SpeedTestState
    data class Running(val mbps: Float, val progress: Float) : SpeedTestState // progress 0..1
    data class Finished(val mbps: Float) : SpeedTestState
    data class Failed(val reason: String) : SpeedTestState
}

/** MVI state for the Diagnose tab: predicted [coverage] per tile, the rolled-up [roomSummaries] and
 * plain-English [findings], and the optimizer's [bestTile]/[tileScores] once it's been run. */
data class DiagnoseState(
    val tab: DiagnoseTab = DiagnoseTab.Coverage,
    val plan: GridPlan? = null,
    val routerPos: Vec2? = null,
    val devicePins: List<DevicePin> = emptyList(),
    val coverage: List<TileCoverage> = emptyList(),
    val worstDevice: Pair<DevicePin, Float>? = null,
    val roomSummaries: List<RoomSummary> = emptyList(),
    val findings: List<Finding> = emptyList(),
    val optimizerState: OptimizerState = OptimizerState.Idle,
    val bestTile: Vec2? = null,
    val bestTileGainDb: Float? = null,
    /** Every walkable tile's optimizer score, for shading the whole grid on the Best Spot screen —
     * [bestTile]/[bestTileGainDb] alone are just the single winning tile, not enough to render a
     * heatmap of the full search. */
    val tileScores: Map<Vec2, Float> = emptyMap(),
    val isComputingCoverage: Boolean = false,
    /** Negotiated Wi-Fi link rate (what the router and phone agreed on), null when not on Wi-Fi. */
    val linkSpeedMbps: Int? = null,
    val isOnWifi: Boolean = false,
    val speedTest: SpeedTestState = SpeedTestState.Idle,
    /** Result of the run before the latest one, so a router change can be compared like-for-like. */
    val previousSpeedMbps: Float? = null,
    /** One-off failure to show the user (e.g. a database write that failed); cleared by [DiagnoseAction.DismissError]. */
    val errorMessage: String? = null,
)

/** User intents on the Diagnose tab; handled by `DiagnoseViewModel.onAction`. */
sealed interface DiagnoseAction {
    data object TabCoverage : DiagnoseAction
    data object TabBestSpot : DiagnoseAction
    data object TabSpeed : DiagnoseAction
    data object RunOptimizer : DiagnoseAction
    data object RunSpeedTest : DiagnoseAction
    data object DismissError : DiagnoseAction

    /** Which room is highlighted on tap is local Compose state in `DiagnoseScreen`, not state here —
     * it's purely a display concern with nothing to persist or coordinate elsewhere. */
    data class MoveRouter(val pos: Vec2) : DiagnoseAction
}
