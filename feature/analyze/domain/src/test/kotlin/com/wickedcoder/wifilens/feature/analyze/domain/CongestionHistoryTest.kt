package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.ChannelCongestion
import kotlin.test.Test
import kotlin.test.assertEquals

class CongestionHistoryTest {
    private val hour = 3_600_000L

    private fun row(hourIndex: Int, channel: Int, score: Int) = ChannelCongestion(hourIndex * hour, "2.4", channel, 1, -50, score)

    @Test
    fun `channels are added up per hour and empty hours stay null`() {
        val rows = listOf(row(10, 1, 30), row(10, 6, 50), row(12, 11, 20))

        val hours = congestionByHour(rows, firstHourStart = 10 * hour, hours = 4)

        assertEquals(listOf(80f, null, 20f, null), hours)
    }

    @Test
    fun `hour start drops minutes and seconds`() {
        assertEquals(5 * hour, hourStart(5 * hour + 59 * 60_000 + 59_000))
    }
}
