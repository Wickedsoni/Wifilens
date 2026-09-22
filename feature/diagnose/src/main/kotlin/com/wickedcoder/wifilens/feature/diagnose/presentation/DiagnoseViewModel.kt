@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // flatMapLatest

package com.wickedcoder.wifilens.feature.diagnose.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wickedcoder.wifilens.core.database.AppSettings
import com.wickedcoder.wifilens.core.database.GridPlanDao
import com.wickedcoder.wifilens.core.database.GridPlanWithCells
import com.wickedcoder.wifilens.core.database.PinDao
import com.wickedcoder.wifilens.core.database.RoomDao
import com.wickedcoder.wifilens.core.database.RouterPinEntity
import com.wickedcoder.wifilens.core.database.SettingsRepository
import com.wickedcoder.wifilens.core.rf.CellType
import com.wickedcoder.wifilens.core.rf.GridPlan
import com.wickedcoder.wifilens.core.rf.Material
import com.wickedcoder.wifilens.core.rf.Vec2
import com.wickedcoder.wifilens.core.rf.bresenhamLine
import com.wickedcoder.wifilens.core.rf.predictRssi
import com.wickedcoder.wifilens.feature.map.domain.DevicePin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private const val POOR_RSSI_THRESHOLD = -75f
private const val FAIR_RSSI_THRESHOLD = -67f
private const val FAIR_DISTANCE_TILES = 7f
private const val POOR_MIN_WALL_COUNT = 2
private const val DEFAULT_ROUTER_BAND = "5"
private const val TAG = "DiagnoseOptimizer"

/**
 * MVI ViewModel for the Diagnose tab: predicted coverage scoring and the router-placement
 * optimizer. Reads the plan/rooms/pins directly from their DAOs rather than through
 * [com.wickedcoder.wifilens.feature.map.domain.MapRepository] — Diagnose runs RF math over the same
 * tables Map writes to, and writes back to them only to apply the optimizer's own suggestion
 * (moving the router pin to the tile it picked).
 */
class DiagnoseViewModel(
    private val gridPlanDao: GridPlanDao,
    private val pinDao: PinDao,
    private val roomDao: RoomDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DiagnoseState())
    val state: StateFlow<DiagnoseState> = _state.asStateFlow()

    private var optimizerJob: Job? = null
    private var roomNames: Map<Int, String> = emptyMap()

    /** Kept in sync from [settingsRepository] so action handlers (RunOptimizer) that
     * run outside the init{} collector still use the current path-loss exponent / reference RSSI. */
    private var settings: AppSettings = AppSettings()

    init {
        val planFlow = gridPlanDao.getActivePlan()
            .flatMapLatest { planWithCells ->
                val planId = planWithCells?.plan?.id
                if (planId == null) {
                    flowOf(Snapshot(plan = null, routerPos = null, devicePins = emptyList(), roomNames = emptyMap()))
                } else {
                    combine(
                        pinDao.observeRouterPin(planId),
                        pinDao.observeDevicePins(planId),
                        roomDao.getRoomsForPlan(planId),
                    ) { router, devices, rooms ->
                        Snapshot(
                            plan = planWithCells.toDomain(),
                            routerPos = router?.let { Vec2(it.x, it.y) },
                            devicePins = devices.map { DevicePin(Vec2(it.x, it.y), it.name) },
                            roomNames = rooms.associate { it.roomId to it.name },
                        )
                    }
                }
            }

        combine(planFlow, settingsRepository.settings) { snapshot, appSettings -> snapshot to appSettings }
            .onEach { (snapshot, appSettings) ->
                roomNames = snapshot.roomNames
                settings = appSettings
                _state.update {
                    it.copy(plan = snapshot.plan, routerPos = snapshot.routerPos, devicePins = snapshot.devicePins)
                }
                recomputeCoverage(snapshot, appSettings)
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: DiagnoseAction) {
        when (action) {
            DiagnoseAction.TabCoverage -> _state.update { it.copy(tab = DiagnoseTab.Coverage) }
            DiagnoseAction.TabBestSpot -> _state.update { it.copy(tab = DiagnoseTab.BestSpot) }
            DiagnoseAction.RunOptimizer -> runOptimizer()
            is DiagnoseAction.MoveRouter -> moveRouter(action.pos)
        }
    }

    private suspend fun recomputeCoverage(snapshot: Snapshot, appSettings: AppSettings) {
        val plan = snapshot.plan
        val router = snapshot.routerPos
        if (plan == null || router == null) {
            _state.update {
                it.copy(coverage = emptyList(), worstDevice = null, roomSummaries = emptyList(), findings = emptyList())
            }
            return
        }

        _state.update { it.copy(isComputingCoverage = true) }

        val coverage = withContext(Dispatchers.Default) {
            buildList {
                for (y in 0 until plan.height) {
                    for (x in 0 until plan.width) {
                        if (plan.cellAt(x, y) is CellType.Floor) {
                            val pos = Vec2(x, y)
                            add(TileCoverage(pos, predictRssi(plan, router, pos, appSettings.referenceRssiAt1m, appSettings.pathLossExponent)))
                        }
                    }
                }
            }
        }

        val coverageByPos = coverage.associateBy { it.pos }

        val worstDevice = snapshot.devicePins
            .map { pin ->
                pin to (coverageByPos[pin.pos]?.rssi
                    ?: predictRssi(plan, router, pin.pos, appSettings.referenceRssiAt1m, appSettings.pathLossExponent))
            }
            .minByOrNull { it.second }

        val roomSummaries = coverage
            .mapNotNull { tile -> (plan.cellAt(tile.pos.x, tile.pos.y) as? CellType.Floor)?.let { it.roomId to tile.rssi } }
            .groupBy({ it.first }, { it.second })
            .map { (roomId, values) -> RoomSummary(roomId, snapshot.roomNames[roomId] ?: "Room $roomId", values.average().toFloat()) }
            .sortedBy { it.roomId }

        val findings = buildFindings(plan, router, snapshot.devicePins, roomSummaries, appSettings)

        _state.update {
            it.copy(
                coverage = coverage,
                worstDevice = worstDevice,
                roomSummaries = roomSummaries,
                findings = findings,
                isComputingCoverage = false,
            )
        }
    }

    /** Only ever reports problems — Good signal or no obstacles never produces a finding. */
    private fun buildFindings(
        plan: GridPlan,
        router: Vec2,
        devices: List<DevicePin>,
        roomSummaries: List<RoomSummary>,
        appSettings: AppSettings,
    ): List<Finding> = buildList {
        roomSummaries.forEach { room ->
            when {
                room.avgRssi < POOR_RSSI_THRESHOLD -> add(Finding(Severity.Poor, "${room.name} has weak signal."))
                room.avgRssi <= FAIR_RSSI_THRESHOLD -> add(Finding(Severity.Fair, "${room.name} has borderline signal."))
            }
        }

        devices.forEach { pin ->
            val rssi = predictRssi(plan, router, pin.pos, appSettings.referenceRssiAt1m, appSettings.pathLossExponent)
            val dx = (pin.pos.x - router.x).toFloat()
            val dy = (pin.pos.y - router.y).toFloat()
            val distanceTiles = sqrt(dx * dx + dy * dy)
            val wallCount = bresenhamLine(router, pin.pos).count { plan.cellAt(it.x, it.y) is CellType.Empty }

            if (rssi < POOR_RSSI_THRESHOLD && wallCount >= POOR_MIN_WALL_COUNT) {
                add(Finding(Severity.Poor, "${pin.name} is behind $wallCount walls with weak signal."))
            } else if (distanceTiles > FAIR_DISTANCE_TILES) {
                add(Finding(Severity.Fair, "${pin.name} is far from the router."))
            }
        }
    }

    private fun runOptimizer() {
        val plan = _state.value.plan ?: return
        val devices = _state.value.devicePins
        val currentRouter = _state.value.routerPos
        if (devices.isEmpty()) return
        val appSettings = settings

        optimizerJob?.cancel()
        optimizerJob = viewModelScope.launch {
            _state.update { it.copy(optimizerState = OptimizerState.Running(0f)) }

            val walkableTiles = withContext(Dispatchers.Default) {
                buildList {
                    for (y in 0 until plan.height) {
                        for (x in 0 until plan.width) {
                            if (plan.isWalkable(x, y)) add(Vec2(x, y))
                        }
                    }
                }
            }
            val total = walkableTiles.size.coerceAtLeast(1)

            var bestTile: Vec2? = null
            var bestWorstCase = Float.NEGATIVE_INFINITY
            var currentWorstCase = Float.NEGATIVE_INFINITY
            val scores = mutableMapOf<Vec2, Float>()

            val elapsedMs = withContext(Dispatchers.Default) {
                val startMs = System.currentTimeMillis()
                walkableTiles.forEachIndexed { index, candidate ->
                    // Worst-case device signal across pins, not the average — a tile that's
                    // great for one device and terrible for another must not beat a balanced one.
                    val worstCase = devices.minOf {
                        predictRssi(plan, candidate, it.pos, appSettings.referenceRssiAt1m, appSettings.pathLossExponent)
                    }
                    scores[candidate] = worstCase
                    if (worstCase > bestWorstCase) {
                        bestWorstCase = worstCase
                        bestTile = candidate
                    }
                    if (candidate == currentRouter) currentWorstCase = worstCase

                    if (index % 25 == 0 || index == walkableTiles.lastIndex) {
                        _state.update { it.copy(optimizerState = OptimizerState.Running((index + 1) / total.toFloat())) }
                    }
                }
                System.currentTimeMillis() - startMs
            }
            Log.d(
                TAG,
                "evaluated ${walkableTiles.size} tiles x ${devices.size} devices " +
                    "(${walkableTiles.size * devices.size} predictRssi calls) in ${elapsedMs}ms",
            )

            if (currentRouter != null && bestTile == currentRouter) {
                _state.update {
                    it.copy(optimizerState = OptimizerState.AlreadyOptimal, bestTile = bestTile, bestTileGainDb = 0f, tileScores = scores)
                }
            } else {
                val gain = if (currentRouter != null) bestWorstCase - currentWorstCase else null
                _state.update {
                    it.copy(optimizerState = OptimizerState.Complete, bestTile = bestTile, bestTileGainDb = gain, tileScores = scores)
                }
            }
        }
    }

    private fun moveRouter(pos: Vec2) {
        viewModelScope.launch {
            val planId = gridPlanDao.getActivePlan().first()?.plan?.id ?: return@launch
            val band = pinDao.observeRouterPin(planId).first()?.band ?: DEFAULT_ROUTER_BAND
            pinDao.insertRouterPin(RouterPinEntity(planId = planId, x = pos.x, y = pos.y, band = band))
            // Router-position change flows back through the init{} collector automatically
            // (observeRouterPin re-emits), which re-triggers recomputeCoverage.
        }
    }
}

private data class Snapshot(
    val plan: GridPlan?,
    val routerPos: Vec2?,
    val devicePins: List<DevicePin>,
    val roomNames: Map<Int, String>,
)

private fun GridPlanWithCells.toDomain(): GridPlan {
    val byPosition = cells.associateBy { it.x to it.y }
    val ordered = (0 until plan.height).flatMap { y ->
        (0 until plan.width).map { x -> byPosition[x to y]?.cellTypeJson ?: CellType.Empty(Material.Drywall) }
    }
    return GridPlan(width = plan.width, height = plan.height, cells = ordered)
}
