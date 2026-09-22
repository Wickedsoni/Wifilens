package com.wickedcoder.wifilens.feature.analyze.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contributions used below (rssiToCongestionContribution): -40 dBm = 85, -45 = 78, -50 = 71,
 * -60 = 57, -70 = 42, -80 = 28, -90 = 14.
 */
class ChannelRecommendationTest {

    private fun net(ssid: String, channel: Int, rssi: Int, band: String = "2.4") =
        ScannedNetwork(ssid = ssid, bssidMasked = "aa:bb··cc", security = "WPA2", rssiDbm = rssi, channel = channel, band = band)

    /** WifiInfo.ssid arrives wrapped in quotes. */
    private fun connected(ssid: String, channel: Int, band: String = "2.4") =
        ConnectedNetwork(ssid = "\"$ssid\"", rssiDbm = -50, channel = channel, band = band)

    @Test
    fun `hidden when not connected`() {
        assertNull(recommendChannel(listOf(net("A", 6, -60)), BandFilter.Band24, connected = null))
    }

    @Test
    fun `hidden when there is no scan data for the band`() {
        assertNull(recommendChannel(listOf(net("A", 36, -60, "5")), BandFilter.Band24, connected("Home", 6)))
    }

    @Test
    fun `2_4 GHz picks the quietest of 1, 6 and 11 excluding the connected channel`() {
        val networks = listOf(
            net("Home", 6, -50), // us
            net("A", 6, -60), // shares our channel: own congestion 57
            net("B", 11, -80), // 28
            net("C", 1, -70), // 42
        )
        assertEquals(
            ChannelAdvice.Switch(channel = 11, band = "2.4", congestionScore = 28, isDfs = false),
            recommendChannel(networks, BandFilter.Band24, connected("Home", 6)),
        )
    }

    @Test
    fun `adjacent-channel networks count against 2_4 GHz candidates`() {
        // Channel 3 leaks into 1 (2 away, 60%: 51) and 6 (3 away, 40%: 34) but not 11 (8 away).
        // We're on 6 (own congestion 34, from that leak); 11 has a weak neighbour of its own (14),
        // still the quietest, and better than staying.
        val networks = listOf(net("Loud", 3, -40), net("Weak", 11, -90))
        assertEquals(
            ChannelAdvice.Switch(channel = 11, band = "2.4", congestionScore = 14, isDfs = false),
            recommendChannel(networks, BandFilter.Band24, connected("Other", 6)),
        )
    }

    @Test
    fun `already optimal when the connected channel is at least as quiet as any alternative`() {
        val networks = listOf(net("Home", 1, -50), net("A", 6, -40), net("B", 11, -45))
        assertEquals(ChannelAdvice.AlreadyOptimal, recommendChannel(networks, BandFilter.Band24, connected("Home", 1)))
    }

    @Test
    fun `the connected AP does not count as congestion on its own channel`() {
        // Only "Home" is around. Counting it would make channel 6 look busier than 1 and 11.
        assertEquals(
            ChannelAdvice.AlreadyOptimal,
            recommendChannel(listOf(net("Home", 6, -40)), BandFilter.Band24, connected("Home", 6)),
        )
    }

    @Test
    fun `5 GHz prefers a non-DFS channel on a tie`() {
        val networks = listOf(36, 40, 44, 48).map { net("N$it", it, -40, "5") } +
            net("Home", 149, -50, "5") + net("Neighbour", 149, -50, "5") // 149 is shared: own 71
        // Every channel from 52 up is empty (0); 153 is the lowest of those that is not DFS.
        assertEquals(
            ChannelAdvice.Switch(channel = 153, band = "5", congestionScore = 0, isDfs = false),
            recommendChannel(networks, BandFilter.Band5, connected("Home", 149, "5")),
        )
    }

    @Test
    fun `5 GHz DFS channel is flagged when it is the only quiet choice`() {
        val nonDfs = listOf(36, 40, 44, 48, 149, 153, 157, 161, 165).map { net("N$it", it, -40, "5") }
        assertEquals(
            ChannelAdvice.Switch(channel = 52, band = "5", congestionScore = 0, isDfs = true),
            recommendChannel(nonDfs, BandFilter.Band5, connected("Home", 36, "5")),
        )
    }

    @Test
    fun `viewing a band other than the connected one excludes nothing`() {
        assertEquals(
            ChannelAdvice.Switch(channel = 40, band = "5", congestionScore = 0, isDfs = false),
            recommendChannel(listOf(net("A", 36, -40, "5")), BandFilter.Band5, connected("Home", 6, band = "2.4")),
        )
    }
}
