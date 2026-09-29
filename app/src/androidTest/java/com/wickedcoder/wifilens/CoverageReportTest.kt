package com.wickedcoder.wifilens

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wickedcoder.wifilens.core.model.CellType
import com.wickedcoder.wifilens.core.model.DevicePin
import com.wickedcoder.wifilens.core.model.GridPlan
import com.wickedcoder.wifilens.core.model.Material
import com.wickedcoder.wifilens.core.model.Vec2
import com.wickedcoder.wifilens.feature.diagnose.domain.Finding
import com.wickedcoder.wifilens.feature.diagnose.domain.FindingKind
import com.wickedcoder.wifilens.feature.diagnose.domain.RoomSummary
import com.wickedcoder.wifilens.feature.diagnose.domain.Severity
import com.wickedcoder.wifilens.feature.diagnose.domain.TileCoverage
import com.wickedcoder.wifilens.feature.diagnose.presentation.DiagnoseState
import com.wickedcoder.wifilens.feature.diagnose.presentation.report.AndroidReportWriter
import com.wickedcoder.wifilens.feature.diagnose.presentation.report.ReportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Writes real report files on the device and reads them back, including the FileProvider URI the share sheet gets. */
class CoverageReportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val writer = AndroidReportWriter(context, Dispatchers.IO)

    private val state = run {
        val width = 12
        val height = 8
        val cells = List(width * height) { i ->
            val x = i % width
            val y = i / width
            when {
                x == 0 || y == 0 || x == width - 1 || y == height - 1 -> CellType.Empty(Material.Brick)
                x == 6 && y == 4 -> CellType.Door
                x == 6 -> CellType.Empty(Material.Drywall)
                x < 6 -> CellType.Floor(1)
                else -> CellType.Floor(2)
            }
        }
        val plan = GridPlan(width, height, cells)
        val coverage = (0 until width * height)
            .filter { cells[it] is CellType.Floor }
            .map { TileCoverage(Vec2(it % width, it / width), -45f - (it % width) * 4f) }
        DiagnoseState(
            plan = plan,
            routerPos = Vec2(2, 3),
            devicePins = listOf(DevicePin(Vec2(9, 5), "TV")),
            coverage = coverage,
            worstDevice = DevicePin(Vec2(9, 5), "TV") to -81f,
            roomSummaries = listOf(RoomSummary(1, "Living room", -52f), RoomSummary(2, null, -78f)),
            findings = listOf(Finding(Severity.Poor, FindingKind.DeviceBehindWalls, "TV", wallCount = 1)),
        )
    }

    @Test
    fun pdfIsOneReadablePageAndShareable() = runBlocking {
        val file = writer.write(state, ReportFormat.Pdf, nowMillis = 1_700_000_000_000L)

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                assertEquals(1, pdf.pageCount)
                pdf.openPage(0).use { page -> assertTrue("at least A4 tall", page.height >= 842) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
        assertEquals("content", uri.scheme)
    }

    @Test
    fun pngIsDoubleResolutionAndReplacesTheOldReport() = runBlocking {
        val pdf = writer.write(state, ReportFormat.Pdf, nowMillis = 1_700_000_000_000L)
        val png = writer.write(state, ReportFormat.Png, nowMillis = 1_700_000_060_000L)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(png.path, options)
        assertEquals(1190, options.outWidth)
        assertTrue(options.outHeight > 600)
        assertTrue("only the newest report is kept", !pdf.exists())
    }
}
