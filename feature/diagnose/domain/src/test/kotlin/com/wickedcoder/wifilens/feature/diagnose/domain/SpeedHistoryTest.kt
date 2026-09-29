package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.SpeedTestRecord
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeedHistoryTest {
    private val zone = ZoneOffset.UTC

    private fun at(hour: Int, mbps: Float, day: Int = 1) =
        SpeedTestRecord(LocalDateTime.of(2026, 9, day, hour, 15).toInstant(zone).toEpochMilli(), mbps)

    @Test
    fun `speeds are averaged per hour of day across days`() {
        val hours = speedByHourOfDay(listOf(at(9, 100f), at(9, 80f, day = 2), at(21, 30f)), zone)

        assertEquals(24, hours.size)
        assertEquals(90f, hours[9])
        assertEquals(30f, hours[21])
        assertNull(hours[12])
    }

    @Test
    fun `a clearly slower hour is called out`() {
        val tests = listOf(at(8, 100f), at(12, 95f), at(16, 105f), at(21, 40f), at(21, 50f, day = 2))

        assertEquals(21, slowestHour(tests, zone))
    }

    @Test
    fun `no pattern without enough spread or a real dip`() {
        assertNull(slowestHour(listOf(at(9, 100f), at(9, 20f), at(9, 50f), at(9, 70f)), zone)) // one hour only
        assertNull(slowestHour(listOf(at(8, 100f), at(12, 98f), at(16, 97f), at(21, 95f)), zone)) // even speeds
    }
}
