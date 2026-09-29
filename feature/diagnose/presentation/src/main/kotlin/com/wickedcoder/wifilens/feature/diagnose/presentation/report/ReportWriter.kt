package com.wickedcoder.wifilens.feature.diagnose.presentation.report

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Picture
import android.graphics.pdf.PdfDocument
import android.text.format.DateFormat
import com.wickedcoder.wifilens.core.common.IoDispatcher
import com.wickedcoder.wifilens.core.designsystem.FAIR_RSSI_DBM
import com.wickedcoder.wifilens.core.designsystem.GOOD_RSSI_DBM
import com.wickedcoder.wifilens.feature.diagnose.domain.Finding
import com.wickedcoder.wifilens.feature.diagnose.domain.FindingKind
import com.wickedcoder.wifilens.feature.diagnose.presentation.DiagnoseState
import com.wickedcoder.wifilens.feature.diagnose.presentation.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.inject.Inject
import kotlin.math.ceil

enum class ReportFormat(val mimeType: String, val extension: String) {
    Pdf("application/pdf", "pdf"),
    Png("image/png", "png"),
}

/** Folder under the cache dir the reports are written to; `res/xml/report_paths.xml` shares exactly this. */
const val REPORT_DIR = "reports"

/** Writes the Coverage tab as a one-page report file, ready to share. */
fun interface ReportWriter {
    suspend fun write(state: DiagnoseState, format: ReportFormat, nowMillis: Long): File
}

/**
 * Draws the report with the platform [android.graphics.Canvas] (no off-screen Compose): into a [PdfDocument] page
 * at least A4 tall, or a PNG exactly as tall as the content. Only the newest report is kept in the cache.
 */
class AndroidReportWriter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ReportWriter {
        override suspend fun write(state: DiagnoseState, format: ReportFormat, nowMillis: Long): File = withContext(ioDispatcher) {
            val content = reportContent(context.resources, context, state, nowMillis)
            // Recorded once to learn the height; the same recording is replayed into the page or bitmap.
            val picture = Picture()
            val height = drawReport(picture.beginRecording(PAGE_WIDTH, PAGE_HEIGHT * 3), content)
            picture.endRecording()

            val dir = File(context.cacheDir, REPORT_DIR).apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            val stamp = DateFormat.format("yyyyMMdd-HHmm", nowMillis)
            val file = File(dir, "wifilens-coverage-$stamp.${format.extension}")
            when (format) {
                ReportFormat.Pdf -> writePdf(picture, maxOf(PAGE_HEIGHT, ceil(height).toInt()), file)
                ReportFormat.Png -> writePng(picture, ceil(height).toInt(), file)
            }
            file
        }

        private fun writePdf(picture: Picture, height: Int, file: File) {
            val document = PdfDocument()
            try {
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, height, 1).create())
                page.canvas.drawPicture(picture)
                document.finishPage(page)
                file.outputStream().use { document.writeTo(it) }
            } finally {
                document.close()
            }
        }

        private fun writePng(picture: Picture, height: Int, file: File) {
            val bitmap = Bitmap.createBitmap((PAGE_WIDTH * PNG_SCALE).toInt(), (height * PNG_SCALE).toInt(), Bitmap.Config.ARGB_8888)
            try {
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.scale(PNG_SCALE, PNG_SCALE)
                canvas.drawPicture(picture)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ReportModule {
    @Binds
    abstract fun bindReportWriter(impl: AndroidReportWriter): ReportWriter
}

/** The Coverage tab's content with its strings resolved, in the same words the screen uses. */
internal fun reportContent(res: Resources, context: Context, state: DiagnoseState, nowMillis: Long): ReportContent {
    val date = Date(nowMillis)
    val calibration = state.calibration
    val findings = state.findings.map { ReportFinding(res.findingText(it), it.severity) }
    return ReportContent(
        title = res.getString(R.string.report_title),
        subtitle = res.getString(
            R.string.report_subtitle,
            DateFormat.getLongDateFormat(context).format(date),
            DateFormat.getTimeFormat(context).format(date),
        ),
        model = if (calibration != null) {
            res.getString(R.string.diagnose_calibrated_intro, calibration.rmseDb)
        } else {
            res.getString(R.string.diagnose_predicted_intro)
        },
        plan = requireNotNull(state.plan) { "a report needs a floor plan" },
        coverage = state.coverage,
        routerPos = state.routerPos,
        devicePins = state.devicePins,
        legend = listOf(
            GOOD_RSSI_DBM.toFloat() to res.getString(R.string.report_legend_good),
            FAIR_RSSI_DBM.toFloat() to res.getString(R.string.report_legend_fair),
            (FAIR_RSSI_DBM - 1).toFloat() to res.getString(R.string.report_legend_poor),
        ),
        weakestDevice = state.worstDevice?.let { (pin, rssi) ->
            res.getString(R.string.report_weakest_device, pin.name, rssi.toInt())
        },
        roomsHeading = res.getString(R.string.report_rooms),
        rooms = state.roomSummaries.map { room ->
            ReportRow(
                label = room.name ?: res.getString(R.string.diagnose_room_unnamed, room.roomId),
                value = res.getString(R.string.diagnose_value_dbm, room.avgRssi.toInt()),
                rssi = room.avgRssi,
            )
        },
        findingsHeading = res.getString(if (findings.isEmpty()) R.string.report_no_findings else R.string.report_findings),
        findings = findings,
        footer = res.getString(R.string.report_footer),
    )
}

/** A finding as one plain-language sentence; the Coverage tab and the report share it. */
internal fun Resources.findingText(finding: Finding): String {
    val subject = finding.subject ?: getString(R.string.diagnose_room_unnamed, finding.roomId ?: 0)
    return when (finding.kind) {
        FindingKind.RoomWeak -> getString(R.string.diagnose_finding_room_weak, subject)
        FindingKind.RoomBorderline -> getString(R.string.diagnose_finding_room_borderline, subject)
        FindingKind.DeviceBehindWalls -> getQuantityString(
            R.plurals.diagnose_finding_device_behind_walls,
            finding.wallCount,
            subject,
            finding.wallCount,
        )
        FindingKind.DeviceFar -> getString(R.string.diagnose_finding_device_far, subject)
    }
}
