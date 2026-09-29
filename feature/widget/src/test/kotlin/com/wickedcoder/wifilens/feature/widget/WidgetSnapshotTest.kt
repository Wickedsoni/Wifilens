package com.wickedcoder.wifilens.feature.widget

import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSnapshotTest {
    private fun connected(rssi: Int, mhz: Int = 5180, link: Int = 866) = WifiConnectionInfo.Connected("\"Home\"", rssi, link, mhz, "aa:bb")

    @Test
    fun `a reading fills the snapshot without the network name`() {
        val snapshot = WidgetSnapshot().next(connected(-58), nowMillis = 1_000)

        assertEquals(WidgetSnapshot(1_000, true, -58, "5", 36, 866, listOf(-58)), snapshot)
    }

    @Test
    fun `disconnecting keeps the band, channel and trend but drops the live values`() {
        val snapshot = WidgetSnapshot().next(connected(-58), 1_000).next(WifiConnectionInfo.Disconnected, 2_000)

        assertFalse(snapshot.connected)
        assertNull(snapshot.rssi)
        assertEquals(listOf(-58), snapshot.trend)
        assertEquals(2_000L, snapshot.updatedAtMillis)
    }

    @Test
    fun `the trend keeps the last three hours`() {
        val snapshot = (1..TREND_SIZE + 3).fold(WidgetSnapshot()) { s, i -> s.next(connected(-40 - i), i.toLong()) }

        assertEquals(TREND_SIZE, snapshot.trend.size)
        assertEquals(-40 - (TREND_SIZE + 3), snapshot.trend.last())
    }

    @Test
    fun `the stored trend survives a round trip and damaged values`() {
        assertEquals(listOf(-60, -62), listOf(-60, -62).encodeTrend().decodeTrend())
        assertEquals(listOf(-60), "-60,oops,".decodeTrend())
        assertEquals(emptyList<Int>(), null.decodeTrend())
    }
}
