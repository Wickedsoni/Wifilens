package com.wickedcoder.wifilens.feature.diagnose.presentation.report

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.wickedcoder.wifilens.core.designsystem.FAIR_RSSI_DBM
import com.wickedcoder.wifilens.core.designsystem.GOOD_RSSI_DBM
import com.wickedcoder.wifilens.core.designsystem.SignalPalette
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.diagnose.domain.Severity
import com.wickedcoder.wifilens.feature.diagnose.domain.TileCoverage
import kotlin.math.min

/** A4 portrait in PDF points; the PNG is the same layout drawn at [PNG_SCALE]. */
internal const val PAGE_WIDTH = 595
internal const val PAGE_HEIGHT = 842
internal const val PNG_SCALE = 2f

private const val MARGIN = 40f
private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
private const val MAP_MAX_HEIGHT = 340f
private const val HEAT_ALPHA = 0x66

/** Everything the report shows, with every string already resolved, so drawing needs no resources. */
internal data class ReportContent(
    val title: String,
    val subtitle: String,
    val model: String,
    val plan: GridPlan,
    val coverage: List<TileCoverage>,
    val routerPos: Vec2?,
    val devicePins: List<DevicePin>,
    val legend: List<Pair<Float, String>>,
    val weakestDevice: String?,
    val roomsHeading: String,
    val rooms: List<ReportRow>,
    val findingsHeading: String,
    val findings: List<ReportFinding>,
    val footer: String,
)

internal data class ReportRow(val label: String, val value: String, val rssi: Float)

internal data class ReportFinding(val text: String, val severity: Severity)

/** Cell bounds of the drawn part of the plan (floors and doors, plus the walls around them), inclusive. */
internal data class CellBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    val width get() = maxX - minX + 1
    val height get() = maxY - minY + 1
}

/**
 * The plan cropped to what was drawn, with one cell of margin for the surrounding walls, so a small flat in a big
 * grid isn't a speck on the page. Null when nothing is drawn yet.
 */
internal fun GridPlan.drawnBounds(): CellBounds? {
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = Int.MIN_VALUE
    var maxY = Int.MIN_VALUE
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (cellAt(x, y) !is CellType.Empty) {
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }
    if (minX == Int.MAX_VALUE) return null
    return CellBounds(
        (minX - 1).coerceAtLeast(0),
        (minY - 1).coerceAtLeast(0),
        (maxX + 1).coerceAtMost(width - 1),
        (maxY + 1).coerceAtMost(height - 1),
    )
}

/** Print colours: the light signal palette always, whatever the phone's theme. */
internal fun printSignalColor(rssi: Float): Int = when {
    rssi >= GOOD_RSSI_DBM -> SignalPalette.goodLight
    rssi >= FAIR_RSSI_DBM -> SignalPalette.fairLight
    else -> SignalPalette.poorLight
}.toArgb()

private const val INK = 0xFF1B1B1F.toInt()
private const val MUTED = 0xFF5E5E66.toInt()
private const val WALL = 0xFF46464F.toInt()
private const val FLOOR = 0xFFF4F3F7.toInt()
private const val RULE = 0xFFD9D9E0.toInt()

/**
 * Draws the report top to bottom at page width [PAGE_WIDTH] and returns the height it used. The caller decides the
 * page: a PDF page at least A4 tall, or a PNG exactly as tall as the content.
 */
internal fun drawReport(canvas: Canvas, content: ReportContent): Float {
    val text = ReportText()
    var y = MARGIN

    y = text.draw(canvas, content.title, text.title, y)
    y = text.draw(canvas, content.subtitle, text.muted, y + 2f)
    y = text.draw(canvas, content.model, text.muted, y + 2f)
    y += 16f

    y = drawPlan(canvas, content, y) + 12f
    y = drawLegend(canvas, content.legend, text, y) + 16f

    content.weakestDevice?.let { y = text.draw(canvas, it, text.body, y) + 12f }

    if (content.rooms.isNotEmpty()) {
        y = text.draw(canvas, content.roomsHeading, text.heading, y) + 4f
        content.rooms.forEach { row -> y = drawRow(canvas, row, text, y) }
        y += 16f
    }

    y = text.draw(canvas, content.findingsHeading, text.heading, y) + 4f
    content.findings.forEach { finding ->
        val dot = if (finding.severity == Severity.Poor) SignalPalette.poorLight.toArgb() else SignalPalette.fairLight.toArgb()
        canvas.drawCircle(MARGIN + 4f, y + 8f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dot })
        y = text.draw(canvas, finding.text, text.body, y, indent = 16f) + 6f
    }

    y += 16f
    y = text.draw(canvas, content.footer, text.small, y)
    return y + MARGIN
}

private fun drawPlan(canvas: Canvas, content: ReportContent, top: Float): Float {
    val plan = content.plan
    val bounds = plan.drawnBounds() ?: return top
    val cell = min(CONTENT_WIDTH / bounds.width, MAP_MAX_HEIGHT / bounds.height)
    val left = MARGIN + (CONTENT_WIDTH - cell * bounds.width) / 2f
    val coverage = content.coverage.associate { it.pos to it.rssi }
    val fill = Paint()

    fun rect(x: Int, y: Int, inset: Float = 0f) = RectF(
        left + (x - bounds.minX) * cell + inset,
        top + (y - bounds.minY) * cell + inset,
        left + (x - bounds.minX + 1) * cell - inset,
        top + (y - bounds.minY + 1) * cell - inset,
    )

    for (y in bounds.minY..bounds.maxY) {
        for (x in bounds.minX..bounds.maxX) {
            when (plan.cellAt(x, y)) {
                is CellType.Floor, CellType.Door -> {
                    fill.color = FLOOR
                    canvas.drawRect(rect(x, y), fill)
                    coverage[Vec2(x, y)]?.let { rssi ->
                        fill.color = (printSignalColor(rssi) and 0x00FFFFFF) or (HEAT_ALPHA shl 24)
                        canvas.drawRect(rect(x, y), fill)
                    }
                }
                is CellType.Empty -> {
                    if (plan.touchesWalkable(x, y)) {
                        fill.color = WALL
                        canvas.drawRect(rect(x, y), fill)
                    }
                }
            }
        }
    }

    val pin = Paint(Paint.ANTI_ALIAS_FLAG)
    val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textSize = 8f
        textAlign = Paint.Align.CENTER
    }
    content.routerPos?.let { pos ->
        val r = rect(pos.x, pos.y)
        pin.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.32f, pin)
        pin.color = INK
        canvas.drawCircle(r.centerX(), r.centerY(), cell * 0.24f, pin)
    }
    content.devicePins.forEach { device ->
        val r = rect(device.pos.x, device.pos.y, inset = cell * 0.3f)
        pin.color = INK
        canvas.drawRect(r, pin)
        canvas.drawText(device.name, r.centerX(), r.bottom + label.textSize + 2f, label)
    }
    return top + cell * bounds.height
}

/** Walls only where they border a floor or door; the rest of the grid is outside the home and stays blank. */
private fun GridPlan.touchesWalkable(x: Int, y: Int): Boolean {
    for (dy in -1..1) {
        for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height && cellAt(nx, ny) !is CellType.Empty) return true
        }
    }
    return false
}

private fun drawLegend(canvas: Canvas, legend: List<Pair<Float, String>>, text: ReportText, top: Float): Float {
    var x = MARGIN
    val swatch = Paint()
    legend.forEach { (rssi, label) ->
        swatch.color = (printSignalColor(rssi) and 0x00FFFFFF) or (HEAT_ALPHA shl 24)
        canvas.drawRect(x, top + 1f, x + 12f, top + 13f, swatch)
        x += 16f
        canvas.drawText(label, x, top + 11f, text.small)
        x += text.small.measureText(label) + 16f
    }
    return top + 14f
}

private fun drawRow(canvas: Canvas, row: ReportRow, text: ReportText, top: Float): Float {
    val baseline = top + text.body.textSize
    canvas.drawText(row.label, MARGIN, baseline, text.body)
    val value = TextPaint(text.body).apply {
        color = printSignalColor(row.rssi)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(row.value, MARGIN + CONTENT_WIDTH, baseline, value)
    val bottom = baseline + 6f
    canvas.drawLine(MARGIN, bottom, MARGIN + CONTENT_WIDTH, bottom, Paint().apply { color = RULE })
    return bottom + 6f
}

/** The report's type scale, in PDF points. */
private class ReportText {
    val title = paint(20f, INK, bold = true)
    val heading = paint(13f, INK, bold = true)
    val body = paint(11f, INK)
    val muted = paint(10f, MUTED)
    val small = paint(9f, MUTED)

    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** Draws wrapped text at [top] and returns the y below it. */
    fun draw(canvas: Canvas, value: String, paint: TextPaint, top: Float, indent: Float = 0f): Float {
        val width = (CONTENT_WIDTH - indent).toInt()
        val layout = StaticLayout.Builder
            .obtain(value, 0, value.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        canvas.save()
        canvas.translate(MARGIN + indent, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }
}
