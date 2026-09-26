package com.wickedcoder.wifilens.feature.analyze.domain

/** Android allows a foreground app about this many scans per rolling window (API 28+). */
private const val SCAN_QUOTA = 4
private const val SCAN_QUOTA_WINDOW_MS = 120_000L

/** Used when the platform refuses a scan but we have not spent the quota ourselves (another app did). */
private const val UNKNOWN_THROTTLE_SECONDS = 30

/** Never tell the user to wait less than this; the estimate is only approximate. */
private const val MIN_WAIT_SECONDS = 5

private const val MILLIS_PER_SECOND = 1000

/** Rounds the remaining milliseconds up to whole seconds. */
private const val ROUND_UP_MILLIS = MILLIS_PER_SECOND - 1

/**
 * Remembers when *we* got the platform to accept a scan, so that when it later refuses one we can predict
 * how long until the oldest accepted scan ages out of the rolling window.
 */
class ScanQuota {
    private val acceptedTimes = ArrayDeque<Long>()

    fun recordAccepted(nowMillis: Long) {
        acceptedTimes.addLast(nowMillis)
        while (acceptedTimes.size > SCAN_QUOTA) acceptedTimes.removeFirst()
    }

    fun estimateWaitSeconds(nowMillis: Long): Int {
        if (acceptedTimes.size < SCAN_QUOTA) return UNKNOWN_THROTTLE_SECONDS
        val freeAtMillis = acceptedTimes.first() + SCAN_QUOTA_WINDOW_MS
        return ((freeAtMillis - nowMillis + ROUND_UP_MILLIS) / MILLIS_PER_SECOND).toInt().coerceAtLeast(MIN_WAIT_SECONDS)
    }

    companion object {
        /** How long a throttle broadcast (no ETA known) is assumed to last. */
        const val WINDOW_SECONDS = (SCAN_QUOTA_WINDOW_MS / MILLIS_PER_SECOND).toInt()
    }
}
