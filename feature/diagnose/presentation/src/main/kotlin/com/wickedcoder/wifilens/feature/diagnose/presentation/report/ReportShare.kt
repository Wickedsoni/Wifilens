package com.wickedcoder.wifilens.feature.diagnose.presentation.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wickedcoder.wifilens.core.designsystem.WifiLensIcon
import com.wickedcoder.wifilens.feature.diagnose.presentation.R
import com.wickedcoder.wifilens.feature.diagnose.presentation.ReportFile

/** "Share report" with a PDF / image choice. Disabled, and relabelled, while a report is being written. */
@Composable
internal fun ShareReportButton(exporting: Boolean, onShare: (ReportFormat) -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { menuOpen = true }, enabled = !exporting) {
            Icon(WifiLensIcon.Share.vector, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(if (exporting) R.string.report_creating else R.string.report_share),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.report_share_pdf)) },
                onClick = {
                    menuOpen = false
                    onShare(ReportFormat.Pdf)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.report_share_png)) },
                onClick = {
                    menuOpen = false
                    onShare(ReportFormat.Png)
                },
            )
        }
    }
}

/** Opens the share sheet for [report] with a read grant on its content URI (no storage permission involved). */
internal fun Context.shareReport(report: ReportFile, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.reports", report.file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(report.format.mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri) // lets the share sheet preview it
    startActivity(Intent.createChooser(send, chooserTitle))
}
