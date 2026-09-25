package com.wickedcoder.wifilens.core.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One nearby access point as last reported by [android.net.wifi.WifiManager.getScanResults]. */
data class WifiScanResult(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val capabilities: String,
)

/** What [wifiScanFlow] emits: a fresh or cached result list, or a reason there isn't one. */
sealed interface WifiScanUpdate {
    data class Results(
        val results: List<WifiScanResult>,
        val timestampMillis: Long,
        /**
         * True only when a real scan just completed (a SCAN_RESULTS_AVAILABLE broadcast that says the
         * results were updated). False for the seed read at start-up and for re-reads after a Wi-Fi/
         * Location change, which show whatever the platform has cached — possibly minutes old.
         */
        val fresh: Boolean = false,
    ) : WifiScanUpdate
    data object Throttled : WifiScanUpdate

    /**
     * The device-wide Location toggle is off. Android hands back an empty `scanResults` list —
     * with no exception and no log — in that state, even with the location permission granted,
     * so it has to be reported explicitly or it looks like "no networks nearby".
     */
    data object LocationDisabled : WifiScanUpdate

    /** The Wi-Fi radio is off. */
    data object WifiOff : WifiScanUpdate
}

/**
 * Emits Wi-Fi scan results, or [WifiScanUpdate.Throttled] when a fresh scan isn't available.
 *
 * `WifiManager.startScan()` was deprecated in API 28 and throttled to ~4 calls/2min per app on
 * API 29+ — on most real devices it just returns `false` and does nothing, so an active poll loop
 * silently goes stale. Below API 29 (where it isn't throttled) this drives scans on an interval;
 * from API 29 on, it stays passive: it consumes whatever scan the system (or another app) already
 * triggers via [WifiManager.SCAN_RESULTS_AVAILABLE_ACTION], and tracks scan *availability* via
 * [WifiManager.ACTION_WIFI_SCAN_AVAILABILITY_CHANGED] to surface [WifiScanUpdate.Throttled] when
 * the platform isn't going to hand us a scan at all (e.g. location services off).
 *
 * Requires ACCESS_FINE_LOCATION (or COARSE on API < 29) *and* the device Location toggle on;
 * CHANGE_WIFI_STATE only matters below API 29.
 *
 * Like every `callbackFlow` here, it seeds itself with [currentWifiScanUpdate] immediately on
 * subscribe (below) rather than waiting for the first broadcast — broadcast receivers only fire on
 * the *next* change, so without a seed emit the screen would show nothing at all until something
 * happens to trigger one.
 */
fun wifiScanFlow(context: Context, activeScanIntervalMillis: Long = 15_000L): Flow<WifiScanUpdate> = callbackFlow {
    val appContext = context.applicationContext
    val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun currentUpdate(fresh: Boolean = false): WifiScanUpdate = currentWifiScanUpdate(appContext, fresh)

    val resultsReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // EXTRA_RESULTS_UPDATED == false means the scan failed, not that the list is empty:
            // the platform still holds the previous results. Emitting an empty list here used to
            // wipe a good list off the screen on every failed scan — but those results aren't
            // fresh either, so only a successful scan counts as "just scanned".
            trySend(currentUpdate(fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, true)))
        }
    }
    registerReceiverCompat(appContext, resultsReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

    // Flipping the Location toggle or the Wi-Fi radio sends no scan broadcast, so without this the
    // screen would stay on its old state until the next system-triggered scan (tens of seconds, or
    // never). Re-reading the whole state handles every direction: off -> WifiOff/LocationDisabled,
    // on -> the (seed) results.
    val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            trySend(currentUpdate())
        }
    }
    registerReceiverCompat(
        appContext,
        stateReceiver,
        IntentFilter().apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        },
    )

    // Surface whatever's already cached immediately rather than waiting for the next
    // system-driven scan, which can be tens of seconds away.
    trySend(currentUpdate())

    // ACTION_WIFI_SCAN_AVAILABILITY_CHANGED exists from API 30 (R); on 29 the broadcast never fires, so
    // API 29 must poll like older releases or Throttled would never be detected there.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val availabilityReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                // EXTRA_SCAN_AVAILABLE is documented (older AOSP) as an int (WIFI_STATE_ENABLED/
                // DISABLED), but on-device testing (Motorola Edge 40, Android 15) shows the
                // platform actually delivers it as a Boolean — getIntExtra() doesn't throw at the
                // call site, it silently returns its default via an internally-caught
                // ClassCastException (Bundle's retrieval is untyped: ask for the wrong type, get
                // the default, not an exception), which permanently broke Throttled detection.
                // Don't assume a type for an OS extra we don't control — inspect what's actually
                // there and fail toward "assume available" rather than toward false throttling.
                val available = when (val value = intent.extras?.get(WifiManager.EXTRA_SCAN_AVAILABLE)) {
                    is Boolean -> value
                    is Int -> value != WifiManager.WIFI_STATE_DISABLED
                    else -> true
                }
                // Scan availability also drops when Wi-Fi or Location is switched off; those have
                // their own states, and a "throttled" countdown on top would be wrong.
                if (!available && wifiManager.isWifiEnabled && isLocationServicesEnabled(appContext)) {
                    trySend(WifiScanUpdate.Throttled)
                }
            }
        }
        registerReceiverCompat(
            appContext,
            availabilityReceiver,
            IntentFilter(WifiManager.ACTION_WIFI_SCAN_AVAILABILITY_CHANGED),
        )

        awaitClose {
            appContext.unregisterReceiver(resultsReceiver)
            appContext.unregisterReceiver(stateReceiver)
            appContext.unregisterReceiver(availabilityReceiver)
        }
    } else {
        val pollJob = launch {
            while (isActive) {
                val started = try {
                    wifiManager.startScan()
                } catch (e: SecurityException) {
                    false
                }
                if (!started) {
                    trySend(WifiScanUpdate.Throttled)
                }
                delay(activeScanIntervalMillis)
            }
        }

        awaitClose {
            appContext.unregisterReceiver(resultsReceiver)
            appContext.unregisterReceiver(stateReceiver)
            pollJob.cancel()
        }
    }
}

/**
 * What a scan could give us right now: [WifiScanUpdate.WifiOff] / [WifiScanUpdate.LocationDisabled]
 * when a prerequisite is missing, otherwise the platform's cached results. Also used by the
 * ViewModel to re-check on resume, so a change made while the app was in the background is caught.
 */
fun currentWifiScanUpdate(context: Context, fresh: Boolean = false): WifiScanUpdate {
    val appContext = context.applicationContext
    val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    if (!wifiManager.isWifiEnabled) return WifiScanUpdate.WifiOff
    if (!isLocationServicesEnabled(appContext)) return WifiScanUpdate.LocationDisabled
    val results = try {
        wifiManager.scanResults.map { it.toDomain() }
    } catch (e: SecurityException) {
        emptyList()
    }
    return WifiScanUpdate.Results(results, System.currentTimeMillis(), fresh)
}

/** Emits the Wi-Fi radio's on/off state now and on every change. */
fun wifiEnabledFlow(context: Context): Flow<Boolean> = callbackFlow {
    val appContext = context.applicationContext
    val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            trySend(wifiManager.isWifiEnabled)
        }
    }
    registerReceiverCompat(appContext, receiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
    trySend(wifiManager.isWifiEnabled)

    awaitClose { appContext.unregisterReceiver(receiver) }
}.distinctUntilChanged()

/** The device-wide Location toggle (Quick Settings / Settings > Location), not the app permission. */
fun isLocationServicesEnabled(context: Context): Boolean {
    val locationManager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(locationManager)
}

/**
 * One user-requested scan. Returns false when the platform refuses it: throttled (foreground apps
 * get ~4 per 2 minutes on API 28+), Wi-Fi off, or missing permission. Results arrive through
 * [wifiScanFlow]'s `SCAN_RESULTS_AVAILABLE_ACTION` receiver — this does not return them.
 */
fun requestWifiScan(context: Context): Boolean {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val started = try {
        @Suppress("DEPRECATION")
        wifiManager.startScan()
    } catch (e: SecurityException) {
        false
    }
    return started
}

private fun registerReceiverCompat(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    } else {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        context.registerReceiver(receiver, filter)
    }
}

private fun android.net.wifi.ScanResult.toDomain() = WifiScanResult(
    ssid = if (SSID.isNullOrBlank()) "[HIDDEN]" else SSID,
    bssid = BSSID.orEmpty(),
    rssi = level,
    frequencyMhz = frequency,
    capabilities = capabilities.orEmpty(),
)

/** 2412-2484 -> "2.4", 5170-5825 -> "5", 5955-7115 -> "6". */
fun Int.toWifiBand(): String = when (this) {
    in 2400..2500 -> "2.4"
    in 5000..5900 -> "5"
    in 5925..7125 -> "6"
    else -> "?"
}

/** Frequency (MHz) -> 802.11 channel number. */
fun Int.toWifiChannel(): Int = when {
    this == 2484 -> 14
    this in 2412..2472 -> (this - 2407) / 5
    this in 5000..5900 -> (this - 5000) / 5
    this in 5925..7125 -> (this - 5950) / 5
    else -> -1
}

/** "[WPA3-SAE-CCMP][ESS]" -> "WPA3". Falls back to "SECURITY NONE" for open networks. */
fun String.toSecurityLabel(): String = when {
    contains("WPA3") -> "WPA3"
    contains("WPA2") -> "WPA2"
    contains("WPA") -> "WPA"
    contains("WEP") -> "WEP"
    else -> "SECURITY NONE"
}

/** "A4:3E:5C:9B:11:1C" -> "A4:3E··1C" (matches the masked-BSSID treatment used across the UI). */
fun String.maskBssid(): String {
    val parts = split(":")
    if (parts.size != 6) return this
    return "${parts[0]}:${parts[1]}··${parts[5]}"
}
