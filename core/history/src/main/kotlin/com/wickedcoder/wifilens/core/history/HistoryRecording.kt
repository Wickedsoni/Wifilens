package com.wickedcoder.wifilens.core.history

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wickedcoder.wifilens.core.common.ApplicationScope
import com.wickedcoder.wifilens.core.common.Clock
import com.wickedcoder.wifilens.core.model.HistoryRepository
import com.wickedcoder.wifilens.core.model.WifiScanRepository
import com.wickedcoder.wifilens.core.model.WifiScanUpdate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HistoryRecorder"

/**
 * Records every fresh scan into history, but only while the app is in the foreground ([start] takes the process
 * lifecycle). It listens passively: it never asks for a scan itself, so it costs no scan quota and no battery beyond
 * what the Analyze tab already does. Background recording would need background location, which the app avoids.
 */
@Singleton
class ScanHistoryRecorder
    @Inject
    constructor(
        private val scans: WifiScanRepository,
        private val history: HistoryRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start(processLifecycle: Lifecycle) {
            scope.launch {
                processLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    scans
                        .observe()
                        .filterIsInstance<WifiScanUpdate.Results>()
                        .filter { it.fresh } // cached re-reads would record the same scan twice
                        .collect { update ->
                            try {
                                history.recordScan(update.results, update.timestampMillis)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "recording a scan failed", e) // history is best-effort; never crash for it
                            }
                        }
                }
            }
        }
    }

/** Daily clean-up of history beyond the retention windows. */
@HiltWorker
class HistoryPruneWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val history: HistoryRepository,
        private val clock: Clock,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = try {
            history.prune(clock.nowMillis())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "pruning history failed", e)
            Result.retry()
        }
    }

private const val PRUNE_WORK_NAME = "history-prune"

/** Enqueues [HistoryPruneWorker] once a day; KEEP so app starts don't reset its schedule. */
fun scheduleHistoryPrune(context: Context) {
    val request = PeriodicWorkRequestBuilder<HistoryPruneWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(PRUNE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
