package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.SecurityIssue
import com.wickedcoder.wifilens.core.model.WifiScanResult
import com.wickedcoder.wifilens.core.model.WifiSecurity
import com.wickedcoder.wifilens.core.model.securityIssues
import com.wickedcoder.wifilens.core.model.toWifiSecurity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkInsightsTest {
    // Capability strings exactly as the Moto Edge 40 reported them (cmd wifi list-scan-results).
    private val transition = "[WPA2-PSK-CCMP-128][RSN-PSK+SAE-CCMP-128][ESS][WPS][MFPC]"
    private val wpa2 = "[WPA2-PSK-CCMP-128][RSN-PSK-CCMP-128][ESS]"
    private val legacy = "[WPA-PSK-TKIP+CCMP-128][WPA2-PSK-TKIP+CCMP-128][RSN-PSK-TKIP+CCMP-128][ESS]"
    private val wpa3Only = "[RSN-SAE-CCMP-128][ESS][MFPR]"

    private fun ap(ssid: String, bssid: String, rssi: Int, mhz: Int, caps: String = wpa2) =
        listOf(WifiScanResult(ssid, bssid, rssi, mhz, caps)).toScannedNetworks().single()

    private fun connectedTo(ssid: String, bssid: String, rssi: Int, mhz: Int) =
        com.wickedcoder.wifilens.core.model.WifiConnectionInfo
            .Connected("\"$ssid\"", rssi, 400, mhz, bssid)
            .toConnectedNetwork()

    @Test
    fun `security is read from the strings Android really writes (B-47)`() {
        assertEquals(WifiSecurity.WPA3, wpa3Only.toWifiSecurity()) // used to be reported as Open
        assertEquals(WifiSecurity.WPA3, transition.toWifiSecurity())
        assertEquals(WifiSecurity.WPA2, wpa2.toWifiSecurity())
        assertEquals(WifiSecurity.WPA2, "[RSN-PSK-CCMP][ESS]".toWifiSecurity())
        assertEquals(WifiSecurity.Open, "[ESS]".toWifiSecurity())
    }

    @Test
    fun `security issues are flagged per capability`() {
        assertEquals(setOf(SecurityIssue.Wpa3Transition), transition.securityIssues())
        assertEquals(emptySet(), wpa2.securityIssues())
        assertEquals(setOf(SecurityIssue.LegacyWpa, SecurityIssue.Tkip), legacy.securityIssues())
        assertEquals(setOf(SecurityIssue.Open), "[ESS]".securityIssues())
        assertEquals(setOf(SecurityIssue.Wep), "[WEP][ESS]".securityIssues())
        assertEquals(emptySet(), wpa3Only.securityIssues())
    }

    @Test
    fun `signal is graded with the app-wide thresholds`() {
        assertEquals(InsightSeverity.Good, signalInsight(-60).severity)
        assertEquals(InsightSeverity.Warning, signalInsight(-72).severity)
        assertEquals(InsightSeverity.Problem, signalInsight(-80).severity)
    }

    @Test
    fun `a same-name network on 5 GHz is suggested when you're on 2_4 GHz`() {
        val networks = listOf(ap("Home", "10:5a:95:45:6c:58", -55, 2417), ap("Home", "10:5a:95:45:6c:5a", -62, 5805))

        val insight = betterBandInsight(connectedTo("Home", "10:5a:95:45:6c:58", -55, 2417), networks)

        assertEquals(Insight.BetterBand("5", -62), insight)
    }

    @Test
    fun `no better-band tip when the faster band is too weak here`() {
        val networks = listOf(ap("Home", "10:5a:95:45:6c:58", -55, 2417), ap("Home", "10:5a:95:45:6c:5a", -86, 5805))

        assertNull(betterBandInsight(connectedTo("Home", "10:5a:95:45:6c:58", -55, 2417), networks))
    }

    @Test
    fun `a clearly stronger mesh node is flagged, but not the same device's other radio`() {
        val connected = connectedTo("Home", "aa:bb:cc:dd:ee:01", -78, 5180)
        val sameDevice = ap("Home", "aa:bb:cc:dd:ee:02", -50, 5200)
        val meshNode = ap("Home", "11:22:33:44:55:01", -60, 5180)

        val insight = strongerAccessPointInsight(connected, listOf(sameDevice, meshNode))

        assertEquals(-60, insight?.rssiDbm)
        assertEquals(Insight.MeshDetected(2), meshInsight(listOf(sameDevice, meshNode)))
        assertNull(meshInsight(listOf(sameDevice)))
    }

    @Test
    fun `a crowded channel suggests the quietest alternative`() {
        val connected = connectedTo("Home", "aa:bb:cc:dd:ee:01", -55, 2437) // channel 6
        val neighbours = listOf(
            ap("Home", "aa:bb:cc:dd:ee:01", -55, 2437),
            ap("N1", "01:00:00:00:00:01", -45, 2437),
            ap("N2", "02:00:00:00:00:01", -50, 2437),
        )

        val insight = assertIs<Insight.CrowdedChannel>(crowdedChannelInsight(connected, neighbours))

        assertEquals(6, insight.channel)
        assertTrue(insight.suggestedChannel in listOf(1, 11))
    }

    @Test
    fun `the check is ordered worst first and empty when not connected`() {
        val networks = listOf(ap("Home", "aa:bb:cc:dd:ee:01", -80, 2437, caps = legacy))

        val insights = buildInsights(connectedTo("Home", "aa:bb:cc:dd:ee:01", -80, 2437), networks, downloadMbps = 50f)

        assertEquals(InsightSeverity.Problem, insights.first().severity) // weak signal
        assertEquals(InsightSeverity.Problem, overallSeverity(insights))
        assertTrue(insights.any { it is Insight.Security && it.issue == SecurityIssue.Tkip })
        assertTrue(buildInsights(null, networks).isEmpty())
    }

    @Test
    fun `the planner picks the quietest channel on every band present`() {
        val networks = listOf(
            ap("N1", "01:00:00:00:00:01", -45, 2412), // ch 1
            ap("N2", "02:00:00:00:00:01", -45, 2462), // ch 11
            ap("N3", "03:00:00:00:00:01", -60, 5180), // ch 36
        )

        val plan = planChannels(networks, connected = null).associateBy { it.band }

        assertEquals(6, plan.getValue("2.4").channel)
        assertEquals(40, plan.getValue("5").channel)
        assertNull(plan["6"])
    }
}
