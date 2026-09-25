package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.AppSettings
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.core.rf.bresenhamLine
import com.wickedcoder.wifilens.core.rf.predictRssi
import kotlin.math.sqrt

private const val POOR_RSSI_THRESHOLD = -75f
private const val FAIR_RSSI_THRESHOLD = -67f
private const val FAIR_DISTANCE_TILES = 7f
private const val POOR_MIN_WALL_COUNT = 2

/**
 * Predicts signal strength for every floor tile and turns it into a report: the weakest device, a
 * per-room average, and plain-English findings. Pure and CPU-heavy on big plans, so callers should run
 * it off the main thread.
 */
class AnalyzeCoverage {
    operator fun invoke(context: PlanContext, settings: AppSettings): CoverageReport {
        val plan = context.plan
        val router = context.routerPos
        if (plan == null || router == null) return CoverageReport.Empty

        val coverage = buildList {
            for (y in 0 until plan.height) {
                for (x in 0 until plan.width) {
                    if (plan.cellAt(x, y) is CellType.Floor) {
                        val pos = Vec2(x, y)
                        add(TileCoverage(pos, predict(plan, router, pos, settings)))
                    }
                }
            }
        }
        val coverageByPos = coverage.associateBy { it.pos }

        val worstDevice = context.devicePins
            .map { pin -> pin to (coverageByPos[pin.pos]?.rssi ?: predict(plan, router, pin.pos, settings)) }
            .minByOrNull { it.second }

        val roomSummaries = coverage
            .mapNotNull { tile -> (plan.cellAt(tile.pos.x, tile.pos.y) as? CellType.Floor)?.let { it.roomId to tile.rssi } }
            .groupBy({ it.first }, { it.second })
            .map { (roomId, values) -> RoomSummary(roomId, context.roomNames[roomId] ?: "Room $roomId", values.average().toFloat()) }
            .sortedBy { it.roomId }

        return CoverageReport(
            coverage = coverage,
            worstDevice = worstDevice,
            roomSummaries = roomSummaries,
            findings = buildFindings(plan, router, context.devicePins, roomSummaries, settings),
        )
    }

    /** Only ever reports problems: good signal or no obstacles never produces a finding. */
    private fun buildFindings(
        plan: GridPlan,
        router: Vec2,
        devices: List<DevicePin>,
        roomSummaries: List<RoomSummary>,
        settings: AppSettings,
    ): List<Finding> = buildList {
        roomSummaries.forEach { room ->
            when {
                room.avgRssi < POOR_RSSI_THRESHOLD -> add(Finding(Severity.Poor, "${room.name} has weak signal."))
                room.avgRssi <= FAIR_RSSI_THRESHOLD -> add(Finding(Severity.Fair, "${room.name} has borderline signal."))
            }
        }

        devices.forEach { pin ->
            val rssi = predict(plan, router, pin.pos, settings)
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

    private fun predict(plan: GridPlan, from: Vec2, to: Vec2, settings: AppSettings): Float =
        predictRssi(plan, from, to, settings.referenceRssiAt1m, settings.pathLossExponent)
}
