package com.wickedcoder.wifilens.core.common

/** Wall-clock source, injected so time-dependent logic (scan throttling, history) is testable with virtual time. */
fun interface Clock {
    fun nowMillis(): Long
}
