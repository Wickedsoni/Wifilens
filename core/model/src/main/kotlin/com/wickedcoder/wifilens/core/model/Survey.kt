package com.wickedcoder.wifilens.core.model

/** A walk-survey reading on a plan tile: running average of the connected network's RSSI (dBm). */
data class Measurement(val pos: Vec2, val rssi: Float, val sampleCount: Int)

/** A plan's fitted prediction model (Sprint 7); when present it replaces the global Settings values for that plan. */
data class PlanCalibration(val referenceRssiAt1m: Float, val pathLossExponent: Float, val rmseDb: Float)
