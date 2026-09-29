package com.wickedcoder.wifilens.feature.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wickedcoder.wifilens.core.designsystem.SignalPalette
import com.wickedcoder.wifilens.core.model.FAIR_SIGNAL_DBM
import com.wickedcoder.wifilens.core.model.GOOD_SIGNAL_DBM
import java.util.Date

private val SMALL = DpSize(110.dp, 48.dp)
private val MEDIUM = DpSize(180.dp, 110.dp)

private val TREND_HEIGHT = 32.dp

/** Widget state keys (per widget instance). */
internal object WidgetKeys {
    val updatedAt = longPreferencesKey("updated_at")
    val connected = booleanPreferencesKey("connected")
    val rssi = intPreferencesKey("rssi")
    val band = stringPreferencesKey("band")
    val channel = intPreferencesKey("channel")
    val linkSpeed = intPreferencesKey("link_speed")
    val trend = stringPreferencesKey("trend")
}

internal fun Preferences.toSnapshot() = WidgetSnapshot(
    updatedAtMillis = this[WidgetKeys.updatedAt],
    connected = this[WidgetKeys.connected] ?: false,
    rssi = this[WidgetKeys.rssi],
    band = this[WidgetKeys.band],
    channel = this[WidgetKeys.channel],
    linkSpeedMbps = this[WidgetKeys.linkSpeed],
    trend = this[WidgetKeys.trend].decodeTrend(),
)

/**
 * Home-screen widget: current signal (dBm in the app's signal colours), band, channel, link speed, a three-hour
 * trend and the refresh time. Tapping opens WifiLens, whose Analyze tab scans on start. Colours follow the
 * wallpaper (GlanceTheme) except the signal value, which keeps its fixed meaning.
 */
class SignalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        provideContent {
            GlanceTheme {
                WidgetContent(currentState<Preferences>().toSnapshot(), launch)
            }
        }
    }
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot, launch: Intent) {
    val context = LocalContext.current
    val large = LocalSize.current.height >= MEDIUM.height
    val muted = GlanceTheme.colors.onSurfaceVariant
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(launch)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rssi = snapshot.rssi
        if (!snapshot.connected || rssi == null) {
            Text(
                text = context.getString(if (snapshot.updatedAtMillis == null) R.string.widget_waiting else R.string.widget_not_on_wifi),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = context.getString(R.string.widget_dbm, rssi),
                    style = TextStyle(color = signalColor(rssi), fontSize = 22.sp, fontWeight = FontWeight.Bold),
                )
                snapshot.band?.let {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(context.getString(R.string.widget_band, it), style = TextStyle(color = muted, fontSize = 13.sp))
                }
            }
            if (large) {
                Text(context.getString(statusLabel(rssi)), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp))
                detailsText(context, snapshot)?.let { Text(it, style = TextStyle(color = muted, fontSize = 12.sp)) }
            }
        }
        if (large) {
            if (snapshot.trend.size >= 2) {
                Spacer(GlanceModifier.height(4.dp))
                Image(
                    provider = ImageProvider(trendBitmap(context, snapshot.trend, muted.getColor(context).toArgb())),
                    contentDescription = context.getString(R.string.widget_trend_description),
                    modifier = GlanceModifier.fillMaxWidth().height(TREND_HEIGHT),
                )
            }
            snapshot.updatedAtMillis?.let {
                val time = DateFormat.getTimeFormat(context).format(Date(it))
                Text(context.getString(R.string.widget_updated, time), style = TextStyle(color = muted, fontSize = 11.sp))
            }
        }
    }
}

private fun detailsText(context: Context, snapshot: WidgetSnapshot): String? {
    val channel = snapshot.channel ?: return null
    val link = snapshot.linkSpeedMbps
    return if (link !=
        null
    ) {
        context.getString(R.string.widget_details, channel, link)
    } else {
        context.getString(R.string.widget_details_no_link, channel)
    }
}

private fun statusLabel(rssi: Int): Int = when {
    rssi >= GOOD_SIGNAL_DBM -> R.string.widget_status_good
    rssi >= FAIR_SIGNAL_DBM -> R.string.widget_status_fair
    else -> R.string.widget_status_weak
}

private fun signalColor(rssi: Int) = when {
    rssi >= GOOD_SIGNAL_DBM -> ColorProvider(day = SignalPalette.goodLight, night = SignalPalette.goodDark)
    rssi >= FAIR_SIGNAL_DBM -> ColorProvider(day = SignalPalette.fairLight, night = SignalPalette.fairDark)
    else -> ColorProvider(day = SignalPalette.poorLight, night = SignalPalette.poorDark)
}

private const val TREND_MIN_DBM = -95f
private const val TREND_MAX_DBM = -30f
private const val TREND_BITMAP_WIDTH = 480
private const val TREND_BITMAP_HEIGHT = 96

/** Glance has no Canvas, so the trend is drawn into a small bitmap, scaled to the widget by the launcher. */
private fun trendBitmap(context: Context, trend: List<Int>, color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(TREND_BITMAP_WIDTH, TREND_BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
    val stroke = context.resources.displayMetrics.density * 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    val step = (TREND_BITMAP_WIDTH - stroke * 2) / (TREND_SIZE - 1)
    val startX = TREND_BITMAP_WIDTH - stroke - step * (trend.size - 1)
    val path = Path()
    trend.forEachIndexed { i, dbm ->
        val fraction = ((dbm - TREND_MIN_DBM) / (TREND_MAX_DBM - TREND_MIN_DBM)).coerceIn(0f, 1f)
        val x = startX + i * step
        val y = stroke + (TREND_BITMAP_HEIGHT - stroke * 2) * (1f - fraction)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    Canvas(bitmap).drawPath(path, paint)
    return bitmap
}

/** Receiver the launcher talks to. First widget added: start the 15-minute refresh; last removed: stop it. */
class SignalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SignalWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresh.schedule(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefresh.refreshNow(context) // a newly added widget shouldn't wait up to 15 minutes for its first reading
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresh.cancel(context)
    }
}
