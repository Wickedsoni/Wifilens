package com.wickedcoder.wifilens.core.wifi

import android.net.wifi.WifiManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ssidFromBytes] must match what the deprecated `ScanResult.SSID` gave, so the network list doesn't change. */
class SsidFromBytesTest {
    @Test
    fun `utf-8 names decode as text`() {
        assertEquals("Home Wi-Fi", ssidFromBytes("Home Wi-Fi".toByteArray()))
        assertEquals("Café 5G ☕", ssidFromBytes("Café 5G ☕".toByteArray()))
    }

    @Test
    fun `hidden network has no bytes`() {
        assertEquals("", ssidFromBytes(ByteArray(0)))
    }

    @Test
    fun `bytes that are not utf-8 read as unknown`() {
        assertEquals(WifiManager.UNKNOWN_SSID, ssidFromBytes(byteArrayOf(0x48, 0xC3.toByte(), 0x28)))
    }
}
