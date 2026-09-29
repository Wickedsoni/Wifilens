package com.wickedcoder.wifilens.feature.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiConnectionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val TAG = "WidgetRefresh"

/** The connection callback answers within milliseconds; this only guards against a platform that never does. */
private const val READ_TIMEOUT_MS = 5_000L

/**
 * Reads the connection once and writes it into every widget's state, then redraws them. Reading the connection
 * needs no location permission for RSSI, frequency and link speed, which is all the widget shows.
 */
@HiltWorker
class WidgetRefreshWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val connection: WifiConnectionRepository,
        private val clock: Clock,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            val ids = GlanceAppWidgetManager(applicationContext).getGlanceIds(SignalWidget::class.java)
            if (ids.isNotEmpty()) {
                val info = withTimeoutOrNull(READ_TIMEOUT_MS) { connection.observe().first() } ?: WifiConnectionInfo.Disconnected
                val now = clock.nowMillis()
                ids.forEach { id ->
                    updateAppWidgetState(applicationContext, id) { prefs ->
                        val next = prefs.toSnapshot().next(info, now)
                        prefs[WidgetKeys.updatedAt] = now
                        prefs[WidgetKeys.connected] = next.connected
                        next.rssi?.let { prefs[WidgetKeys.rssi] = it } ?: prefs.remove(WidgetKeys.rssi)
                        next.band?.let { prefs[WidgetKeys.band] = it }
                        next.channel?.let { prefs[WidgetKeys.channel] = it }
                        next.linkSpeedMbps?.let { prefs[WidgetKeys.linkSpeed] = it } ?: prefs.remove(WidgetKeys.linkSpeed)
                        prefs[WidgetKeys.trend] = next.trend.encodeTrend()
                    }
                }
                SignalWidget().updateAll(applicationContext)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "widget refresh failed", e)
            Result.retry()
        }
    }

/** Scheduling for [WidgetRefreshWorker]: every 15 minutes (WorkManager's minimum) while a widget exists. */
object WidgetRefresh {
    private const val PERIODIC = "widget-refresh"
    private const val NOW = "widget-refresh-now"
    private const val PERIOD_MINUTES = 15L

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIOD_MINUTES, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One refresh as soon as possible (a widget was added, or the app was opened). No-op work without widgets. */
    fun refreshNow(context: Context) {
        WorkManager
            .getInstance(
                context,
            ).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
