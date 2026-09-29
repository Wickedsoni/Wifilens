package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiScanResult
import com.wickedcoder.wifilens.core.model.maskBssid
import com.wickedcoder.wifilens.core.model.toWifiBand
import com.wickedcoder.wifilens.core.model.toWifiChannel
import com.wickedcoder.wifilens.core.model.toWifiSecurity

/** Android wraps SSIDs in quotes and returns "<unknown ssid>" when it may not reveal the name (missing location
 * permission, or location services off). Null means "name hidden"; the UI phrases that from resources. */
fun String.toDisplaySsid(): String? =
    removeSurrounding("\"").takeUnless { it.isBlank() || it == UNKNOWN_SSID }

private const val UNKNOWN_SSID = "<unknown ssid>"

fun WifiConnectionInfo.Connected.toConnectedNetwork(): ConnectedNetwork = ConnectedNetwork(
    ssid = ssid.toDisplaySsid(),
    rssiDbm = rssi,
    channel = frequencyMhz.toWifiChannel(),
    band = frequencyMhz.toWifiBand(),
)

// distinctBy: some drivers report an access point twice in one scan; the BSSID is the list key, so it must be unique.
fun List<WifiScanResult>.toScannedNetworks(): List<ScannedNetwork> = distinctBy { it.bssid }.map { result ->
    ScannedNetwork(
        ssid = result.ssid,
        bssidMasked = result.bssid.maskBssid(),
        security = result.capabilities.toWifiSecurity(),
        rssiDbm = result.rssi,
        channel = result.frequencyMhz.toWifiChannel(),
        band = result.frequencyMhz.toWifiBand(),
        id = result.bssid,
    )
}
