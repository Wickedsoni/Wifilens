package com.wickedcoder.wifilens.core.model

import kotlinx.coroutines.flow.Flow

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

/** Wi-Fi scanning. Implemented in `:core:wifi` on top of `WifiManager`; faked in tests. */
interface WifiScanRepository {
    /** Scan results and availability changes as they happen (also seeds the current state on collect). */
    fun observe(): Flow<WifiScanUpdate>

    /** Asks the platform for one scan; false when it refuses (throttled or radio busy). */
    fun startScan(): Boolean

    /** Re-reads Wi-Fi, Location and cached results right now. */
    fun currentUpdate(): WifiScanUpdate
}

// Wi-Fi frequency plan (MHz). Ranges are slightly generous so real-world centre frequencies always land in one.
private val BAND_24_MHZ = 2400..2500
private val BAND_5_MHZ = 5000..5900
private val BAND_6_MHZ = 5925..7125

/** 2.4 GHz channels 1-13 are 5 MHz apart from 2412; channel 14 is the odd one out at 2484. */
private val CHANNELS_1_TO_13_MHZ = 2412..2472
private const val CHANNEL_14_MHZ = 2484
private const val CHANNEL_14 = 14
private const val CHANNEL_1_BASE_MHZ = 2407
private const val CHANNEL_SPACING_MHZ = 5
private const val BAND_5_BASE_MHZ = 5000
private const val BAND_6_BASE_MHZ = 5950
private const val UNKNOWN_CHANNEL = -1

/** 2412-2484 -> "2.4", 5170-5825 -> "5", 5955-7115 -> "6". */
fun Int.toWifiBand(): String = when (this) {
    in BAND_24_MHZ -> "2.4"
    in BAND_5_MHZ -> "5"
    in BAND_6_MHZ -> "6"
    else -> "?"
}

/** Frequency (MHz) -> 802.11 channel number. */
fun Int.toWifiChannel(): Int = when {
    this == CHANNEL_14_MHZ -> CHANNEL_14
    this in CHANNELS_1_TO_13_MHZ -> (this - CHANNEL_1_BASE_MHZ) / CHANNEL_SPACING_MHZ
    this in BAND_5_MHZ -> (this - BAND_5_BASE_MHZ) / CHANNEL_SPACING_MHZ
    this in BAND_6_MHZ -> (this - BAND_6_BASE_MHZ) / CHANNEL_SPACING_MHZ
    else -> UNKNOWN_CHANNEL
}

/** Strongest security a network advertises. The UI shows the technical names as-is and localises [Open]. */
enum class WifiSecurity { WPA3, WPA2, WPA, WEP, Open }

/**
 * Strongest security in a scan result's capabilities, as Android actually writes them (checked on a Moto Edge 40):
 * WPA3 appears as SAE (`[RSN-SAE-CCMP]`, or `[RSN-PSK+SAE-CCMP]` in WPA2/WPA3 transition mode) and never as "WPA3";
 * newer Android versions write WPA2 as `RSN`. OWE (Enhanced Open) is encrypted, so it counts as WPA3-era, not open.
 * B-47: this used to look for "WPA3" and "WPA" only, so a WPA3-only network was reported as Open.
 */
fun String.toWifiSecurity(): WifiSecurity = when {
    contains("SAE") || contains("OWE") || contains("WPA3") -> WifiSecurity.WPA3
    contains("RSN") || contains("WPA2") -> WifiSecurity.WPA2
    contains("WPA") -> WifiSecurity.WPA
    contains("WEP") -> WifiSecurity.WEP
    else -> WifiSecurity.Open
}

/** Weaknesses a network's security setup can have, strongest concern first. */
enum class SecurityIssue {
    /** No encryption: anyone nearby can read the traffic. */
    Open,

    /** WEP can be cracked in minutes. */
    Wep,

    /** Original WPA (WPA1) is still offered, which keeps old, weaker handshakes available. */
    LegacyWpa,

    /** TKIP encryption is offered; it's deprecated and caps speed on many routers. */
    Tkip,

    /** WPA2/WPA3 transition mode: fine for compatibility, but WPA3-only is stronger once every device supports it. */
    Wpa3Transition,
}

/** Every [SecurityIssue] in a capabilities string such as `[WPA-PSK-TKIP+CCMP][WPA2-PSK-TKIP+CCMP][ESS]`. */
fun String.securityIssues(): Set<SecurityIssue> {
    val security = toWifiSecurity()
    return setOfNotNull(
        SecurityIssue.Open.takeIf { security == WifiSecurity.Open },
        SecurityIssue.Wep.takeIf { security == WifiSecurity.WEP },
        SecurityIssue.LegacyWpa.takeIf { contains("[WPA-") },
        SecurityIssue.Tkip.takeIf { contains("TKIP") },
        SecurityIssue.Wpa3Transition.takeIf { contains("SAE") && contains("PSK") },
    )
}

/** At or above: good signal (reliable video calls). Shared by every screen and rule that grades signal. */
const val GOOD_SIGNAL_DBM = -67f

/** At or above (and below [GOOD_SIGNAL_DBM]): fair. Below: weak. */
const val FAIR_SIGNAL_DBM = -75f

/** "A4:3E:5C:9B:11:1C" -> "A4:3E··1C" (matches the masked-BSSID treatment used across the UI). */
private const val BSSID_OCTETS = 6

fun String.maskBssid(): String {
    val parts = split(":")
    if (parts.size != BSSID_OCTETS) return this
    return "${parts.first()}:${parts[1]}··${parts.last()}"
}
