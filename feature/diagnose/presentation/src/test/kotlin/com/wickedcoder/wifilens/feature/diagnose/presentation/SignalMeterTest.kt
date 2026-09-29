package com.wickedcoder.wifilens.feature.diagnose.presentation

import com.wickedcoder.wifilens.core.model.WifiConnectionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalMeterTest {
    private fun connected(rssi: Int, mhz: Int = 5180, link: Int = 866) = WifiConnectionInfo.Connected("Home", rssi, link, mhz)

    @Test
    fun `readings update the value, band, link and best and worst`() {
        val meter = listOf(connected(-60), connected(-52), connected(-71, mhz = 2437, link = 0))
            .fold(SignalMeterState()) { m, info -> m.next(info) }

        assertEquals(-71, meter.rssi)
        assertEquals("2.4", meter.band)
        assertNull(meter.linkSpeedMbps) // 0 means Android doesn't know the rate
        assertEquals(-52, meter.best)
        assertEquals(-71, meter.worst)
        assertEquals(listOf(-60, -52, -71), meter.history)
    }

    @Test
    fun `a disconnect clears the live value but keeps the session`() {
        val meter = SignalMeterState().next(connected(-60)).next(WifiConnectionInfo.Disconnected)

        assertNull(meter.rssi)
        assertNull(meter.band)
        assertEquals(listOf(-60), meter.history)
        assertEquals(-60, meter.best)
    }

    @Test
    fun `the trace keeps only the last minute`() {
        val meter = (1..METER_HISTORY_SIZE + 5).fold(SignalMeterState()) { m, i -> m.next(connected(-i)) }

        assertEquals(METER_HISTORY_SIZE, meter.history.size)
        assertEquals(-(METER_HISTORY_SIZE + 5), meter.history.last())
    }
}
