package com.wickedcoder.wifilens.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsampleTest {
    @Test
    fun `short series are returned untouched`() {
        val points = List(10) { ChartPoint(it.toFloat(), -60f) }

        assertEquals(points, downsampleMinMax(points, buckets = 10))
    }

    @Test
    fun `long series shrink but keep every dip and spike`() {
        val points = List(10_000) { i ->
            val y = when (i) {
                4_321 -> -90f // a brief dead spot
                8_765 -> -30f // standing next to the router
                else -> -60f
            }
            ChartPoint(i.toFloat(), y)
        }

        val reduced = downsampleMinMax(points, buckets = 100)

        assertTrue("size ${reduced.size}", reduced.size <= 200)
        assertTrue(reduced.any { it.y == -90f })
        assertTrue(reduced.any { it.y == -30f })
        assertEquals(reduced.sortedBy { it.x }, reduced) // still in time order
    }
}
