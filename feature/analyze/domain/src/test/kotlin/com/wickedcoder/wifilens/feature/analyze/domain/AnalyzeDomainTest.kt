package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import com.wickedcoder.wifilens.core.model.WifiScanResult
import com.wickedcoder.wifilens.core.model.maskBssid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyzeDomainTest {
    private fun net(ssid: String, channel: Int, rssi: Int, band: String = "2.4") =
        ScannedNetwork(ssid = ssid, bssidMasked = "aa:bb··cc", security = "WPA2", rssiDbm = rssi, channel = channel, band = band)

    // ---- BuildSpectrum --------------------------------------------------------------------------

    @Test
    fun `bars are grouped per channel and sorted with the loudest signal as the peak`() {
        val summary = BuildSpectrum()(
            listOf(net("A", 6, -60), net("B", 1, -70), net("C", 6, -50)),
            BandFilter.Band24,
            connected = null,
        )

        assertEquals(listOf(1, 6), summary.bars.map { it.channel })
        val six = summary.bars.last()
        assertEquals(-50, six.peakRssiDbm)
        assertEquals(listOf("A", "C"), six.networkLabels)
    }

    @Test
    fun `congestion is capped at 100`() {
        val loud = List(5) { net("N$it", 6, -35) }

        val summary = BuildSpectrum()(loud, BandFilter.Band24, connected = null)

        assertEquals(100, summary.bars.single().congestionScore)
    }

    @Test
    fun `only networks in the selected band are counted`() {
        val summary = BuildSpectrum()(listOf(net("Two", 6, -60), net("Five", 36, -60, "5")), BandFilter.Band5, connected = null)

        assertEquals(listOf(36), summary.bars.map { it.channel })
    }

    @Test
    fun `co-channel, overlapping and strongest interferer are relative to the connected network`() {
        val connected = ConnectedNetwork(ssid = "Home", rssiDbm = -45, channel = 6, band = "2.4")
        val networks = listOf(
            net("Same", 6, -70),
            net("Near", 5, -55), // overlaps (1 channel away)
            net("Far", 11, -40), // 5 away: neither
            net("Home", 6, -45),
        )

        val stats = BuildSpectrum()(networks, BandFilter.Band24, connected).stats

        assertEquals(2, stats.coChannelCount) // "Same" and the connected AP itself
        assertEquals(1, stats.overlappingCount)
        assertEquals(-40, stats.strongestInterfererDbm)
    }

    @Test
    fun `no connection means zero counts and no advice`() {
        val summary = BuildSpectrum()(listOf(net("A", 6, -60)), BandFilter.Band24, connected = null)

        assertEquals(0, summary.stats.coChannelCount)
        assertEquals(0, summary.stats.overlappingCount)
        assertNull(summary.advice)
    }

    // ---- ScanQuota ------------------------------------------------------------------------------

    @Test
    fun `wait is unknown until the quota has been spent`() {
        val quota = ScanQuota()
        repeat(3) { quota.recordAccepted(it * 1_000L) }

        assertEquals(30, quota.estimateWaitSeconds(nowMillis = 10_000))
    }

    @Test
    fun `once spent the wait is counted from the oldest accepted scan`() {
        val quota = ScanQuota()
        listOf(0L, 10_000L, 20_000L, 30_000L).forEach(quota::recordAccepted)

        assertEquals(80, quota.estimateWaitSeconds(nowMillis = 40_000)) // oldest ages out at 120 s
    }

    @Test
    fun `wait is never less than five seconds`() {
        val quota = ScanQuota()
        listOf(0L, 10_000L, 20_000L, 30_000L).forEach(quota::recordAccepted)

        assertEquals(5, quota.estimateWaitSeconds(nowMillis = 119_500))
    }

    @Test
    fun `only the four most recent scans count`() {
        val quota = ScanQuota()
        listOf(0L, 10_000L, 20_000L, 30_000L, 40_000L).forEach(quota::recordAccepted)

        assertEquals(70, quota.estimateWaitSeconds(nowMillis = 60_000)) // oldest kept is t=10 s -> frees at 130 s
    }

    @Test
    fun `window constant matches the platform quota window`() {
        assertEquals(120, ScanQuota.WINDOW_SECONDS)
    }

    // ---- mapping --------------------------------------------------------------------------------

    @Test
    fun `quoted ssids lose their quotes and unknown ones get a generic name`() {
        assertEquals("Home", "\"Home\"".toDisplaySsid())
        assertEquals("Connected network", "<unknown ssid>".toDisplaySsid())
        assertEquals("Connected network", "   ".toDisplaySsid())
    }

    @Test
    fun `a connection maps to channel and band`() {
        val connected = WifiConnectionInfo.Connected(ssid = "\"Home\"", rssi = -50, linkSpeedMbps = 866, frequencyMhz = 5180)

        val network = connected.toConnectedNetwork()

        assertEquals(ConnectedNetwork("Home", -50, 36, "5"), network)
    }

    @Test
    fun `scan results are mapped with masked bssid and security label`() {
        val result = WifiScanResult("Cafe", "10:5a:17:12:34:58", -61, 2437, "[WPA3-SAE-CCMP][ESS]")

        val mapped = listOf(result).toScannedNetworks().single()

        assertEquals("10:5a:17:12:34:58".maskBssid(), mapped.bssidMasked)
        assertEquals("WPA3", mapped.security)
        assertEquals(6, mapped.channel)
        assertEquals("2.4", mapped.band)
        assertTrue(mapped.rssiDbm == -61)
    }
}
