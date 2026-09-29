package com.wickedcoder.wifilens.feature.analyze.domain

import com.wickedcoder.wifilens.core.model.ChannelCongestion
import java.util.concurrent.TimeUnit

private val HOUR_MS = TimeUnit.HOURS.toMillis(1)

/**
 * Total congestion of a band for each of [hours] consecutive clock hours starting at [firstHourStart] (all channels
 * added up), for the "Busy hours" chart. Null marks an hour with nothing recorded (the app wasn't open), so a gap
 * isn't mistaken for a quiet hour.
 */
fun congestionByHour(rows: List<ChannelCongestion>, firstHourStart: Long, hours: Int): List<Float?> {
    val totals = rows.groupBy { it.hourStartMillis }.mapValues { (_, channels) -> channels.sumOf { it.congestionScore }.toFloat() }
    return List(hours) { i -> totals[firstHourStart + i * HOUR_MS] }
}

/** Start of the clock hour containing [millis] (UTC-aligned, which matches local hours in whole-hour time zones). */
fun hourStart(millis: Long): Long = millis - millis % HOUR_MS
