package com.wickedcoder.wifilens.feature.map.data

import com.wickedcoder.wifilens.core.database.MeasurementEntity
import com.wickedcoder.wifilens.core.model.Measurement
import com.wickedcoder.wifilens.core.model.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

class MergePerTileTest {
    private fun row(x: Int, y: Int, bssid: String, rssi: Double, samples: Int) =
        MeasurementEntity(planId = 1, x = x, y = y, bssid = bssid, rssiAvg = rssi, sampleCount = samples, updatedAt = 0)

    @Test
    fun `one access point per tile passes through`() {
        val merged = mergePerTile(listOf(row(2, 3, "a", -55.0, 6)))

        assertEquals(listOf(Measurement(Vec2(2, 3), -55f, 6)), merged)
    }

    @Test
    fun `on a shared tile the access point read most often wins`() {
        val merged = mergePerTile(listOf(row(1, 1, "mesh-node", -70.0, 2), row(1, 1, "router", -60.0, 12)))

        assertEquals(listOf(Measurement(Vec2(1, 1), -60f, 12)), merged)
    }

    @Test
    fun `tiles come out in row-major order`() {
        val merged = mergePerTile(listOf(row(5, 2, "a", -50.0, 1), row(0, 2, "a", -50.0, 1), row(9, 0, "a", -50.0, 1)))

        assertEquals(listOf(Vec2(9, 0), Vec2(0, 2), Vec2(5, 2)), merged.map { it.pos })
    }
}
