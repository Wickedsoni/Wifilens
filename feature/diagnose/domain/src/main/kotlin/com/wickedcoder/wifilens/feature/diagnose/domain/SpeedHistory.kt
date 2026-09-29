package com.wickedcoder.wifilens.feature.diagnose.domain

import com.wickedcoder.wifilens.core.model.SpeedTestRecord
import java.time.Instant
import java.time.ZoneId

const val HOURS_PER_DAY = 24

/** A pattern needs this many tests spread over at least [MIN_HOURS_FOR_PATTERN] different hours. */
const val MIN_TESTS_FOR_PATTERN = 4
const val MIN_HOURS_FOR_PATTERN = 3

/** A slow hour is called out only if it averages at least this much below the overall average. */
private const val SLOW_HOUR_FRACTION = 0.8f

/** Average download speed for each hour of the day (0–23, local time in [zone]); null for hours with no test. */
fun speedByHourOfDay(records: List<SpeedTestRecord>, zone: ZoneId): List<Float?> {
    val byHour = records.groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).hour }
    return List(HOURS_PER_DAY) { hour -> byHour[hour]?.map { it.downloadMbps }?.average()?.toFloat() }
}

/**
 * The hour of day whose average speed is clearly below the overall average (the "slower evenings" pattern), or
 * null when there isn't enough data or no hour stands out.
 */
fun slowestHour(records: List<SpeedTestRecord>, zone: ZoneId): Int? {
    val hours = speedByHourOfDay(records, zone)
    if (records.size < MIN_TESTS_FOR_PATTERN || hours.count { it != null } < MIN_HOURS_FOR_PATTERN) return null
    val overall = records.map { it.downloadMbps }.average().toFloat()
    val (hour, speed) = hours.withIndex().mapNotNull { (h, v) -> v?.let { h to it } }.minBy { it.second }
    return hour.takeIf { speed <= overall * SLOW_HOUR_FRACTION }
}
