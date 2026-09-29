package com.wickedcoder.wifilens

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.wickedcoder.wifilens.core.history.ScanHistoryRecorder
import com.wickedcoder.wifilens.core.history.scheduleHistoryPrune
import com.wickedcoder.wifilens.feature.widget.WidgetRefresh
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt's component root. Referenced from `AndroidManifest.xml`. It also supplies WorkManager's configuration (so
 * workers are Hilt-injected; the default initializer is removed in the manifest) and starts foreground-only history
 * recording.
 */
@HiltAndroidApp
class WifiLensApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var scanHistoryRecorder: ScanHistoryRecorder

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        scanHistoryRecorder.start(ProcessLifecycleOwner.get().lifecycle)
        scheduleHistoryPrune(this)
        // Opening the app freshens the widget too: on every return to the foreground, not only on a cold start (B-48).
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = WidgetRefresh.refreshNow(this@WifiLensApplication)
            },
        )
    }
}
