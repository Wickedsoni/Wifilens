package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiScanResult
import com.wickedcoder.wifilens.core.model.maskBssid
import com.wickedcoder.wifilens.core.model.toSecurityLabel
import com.wickedcoder.wifilens.core.model.toWifiBand
import com.wickedcoder.wifilens.core.model.toWifiChannel

/** Android wraps SSIDs in quotes and returns "<unknown ssid>" when it may not reveal the name
 * (missing location/nearby-devices permission, or location services off). */
fun String.toDisplaySsid(): String =
    removeSurrounding("\"").takeUnless { it.isBlank() || it == "<unknown ssid>" } ?: "Connected network"

fun WifiConnectionInfo.Connected.toConnectedNetwork(): ConnectedNetwork = ConnectedNetwork(
    ssid = ssid.toDisplaySsid(),
    rssiDbm = rssi,
    channel = frequencyMhz.toWifiChannel(),
    band = frequencyMhz.toWifiBand(),
)

fun List<WifiScanResult>.toScannedNetworks(): List<ScannedNetwork> = map { result ->
    ScannedNetwork(
        ssid = result.ssid,
        bssidMasked = result.bssid.maskBssid(),
        security = result.capabilities.toSecurityLabel(),
        rssiDbm = result.rssi,
        channel = result.frequencyMhz.toWifiChannel(),
        band = result.frequencyMhz.toWifiBand(),
    )
}
