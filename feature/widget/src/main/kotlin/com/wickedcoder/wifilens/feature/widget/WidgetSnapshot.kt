package com.wickedcoder.wifilens.feature.widget

import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.toWifiBand
import com.wickedcoder.wifilens.core.model.toWifiChannel

/** Readings the trend line keeps: at one per 15-minute refresh, the last three hours. */
internal const val TREND_SIZE = 12

/**
 * What the widget shows, as of its last refresh. Deliberately no network name or BSSID: those count as location
 * data in the background, and the app doesn't ask for background location.
 */
internal data class WidgetSnapshot(
    /** Null before the first refresh. */
    val updatedAtMillis: Long? = null,
    val connected: Boolean = false,
    val rssi: Int? = null,
    val band: String? = null,
    val channel: Int? = null,
    val linkSpeedMbps: Int? = null,
    /** Oldest first; only connected readings. */
    val trend: List<Int> = emptyList(),
)

/** Folds one connection reading taken at [nowMillis] into the snapshot. */
internal fun WidgetSnapshot.next(info: WifiConnectionInfo, nowMillis: Long): WidgetSnapshot = when (info) {
    is WifiConnectionInfo.Connected -> WidgetSnapshot(
        updatedAtMillis = nowMillis,
        connected = true,
        rssi = info.rssi,
        band = info.frequencyMhz.toWifiBand().takeIf { it != "?" },
        channel = info.frequencyMhz.toWifiChannel().takeIf { it > 0 },
        linkSpeedMbps = info.linkSpeedMbps.takeIf { it > 0 },
        trend = (trend + info.rssi).takeLast(TREND_SIZE),
    )
    WifiConnectionInfo.Disconnected -> copy(updatedAtMillis = nowMillis, connected = false, rssi = null, linkSpeedMbps = null)
}

/** The trend as stored in widget state ("-60,-62,-58"); tolerant of a missing or damaged value. */
internal fun List<Int>.encodeTrend(): String = joinToString(",")

internal fun String?.decodeTrend(): List<Int> = this?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
