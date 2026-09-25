package com.wickedcoder.wifilens.core.model

import kotlinx.coroutines.flow.Flow

/** Progress of one [downloadSpeedFlow] run. */
sealed interface SpeedTestUpdate {
    /** [mbps] is the running average so far; [fraction] is how much of the time budget is used (0..1). */
    data class Running(val mbps: Float, val fraction: Float) : SpeedTestUpdate

    data class Finished(val mbps: Float) : SpeedTestUpdate

    data class Failed(val reason: String) : SpeedTestUpdate
}

/** Runs a download speed test. Implemented in `:core:wifi` (real network) and faked in tests. */
interface SpeedTestRepository {
    fun run(): Flow<SpeedTestUpdate>
}
