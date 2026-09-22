package com.wickedcoder.wifilens.feature.map.domain

import com.wickedcoder.wifilens.core.rf.Vec2

/** A placed Wi-Fi client the user wants coverage checked at (e.g. "Laptop" at a given tile). */
data class DevicePin(val pos: Vec2, val name: String)
