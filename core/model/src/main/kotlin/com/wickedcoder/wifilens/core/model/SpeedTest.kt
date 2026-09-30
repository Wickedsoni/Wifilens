package com.wickedcoder.wifilens.core.model

import kotlinx.coroutines.flow.Flow

/** Progress of one [downloadSpeedFlow] run. */
sealed interface SpeedTestUpdate {
    /** [mbps] is the running average so far; [fraction] is how much of the time budget is used (0..1). */
    data class Running(val mbps: Float, val fraction: Float) : SpeedTestUpdate

    data class Finished(val mbps: Float) : SpeedTestUpdate

    /**
     * [blocked] is true when the network actively refused the test (connection reset, TLS failure, HTTP 403): common on
     * school and office Wi-Fi, where "check your internet" would be wrong advice (B-54).
     */
    data class Failed(val reason: String, val blocked: Boolean = false) : SpeedTestUpdate
}

/** Runs a download speed test. Implemented in `:core:wifi` (real network) and faked in tests. */
interface SpeedTestRepository {
    fun run(): Flow<SpeedTestUpdate>
}
