package com.orientesanasrekinatajs.ui

import com.orientesanasrekinatajs.ui.components.pinnedRouteStrokeWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRotationTest {

    @Test
    fun rotateButtonChoosesNextClockwiseCardinalDirection() {
        assertEquals(1, nextClockwiseQuarterTurn(0, 0f))
        assertEquals(1, nextClockwiseQuarterTurn(0, 30f))
        assertEquals(0, nextClockwiseQuarterTurn(0, -30f))
        assertEquals(4, nextClockwiseQuarterTurn(3, 45f))
    }

    @Test
    fun saveChoosesClosestCardinalDirection() {
        assertEquals(0, nearestQuarterTurn(0, 30f))
        assertEquals(1, nearestQuarterTurn(0, 50f))
        assertEquals(0, nearestQuarterTurn(0, -30f))
        assertEquals(-1, nearestQuarterTurn(0, -50f))
    }

    @Test
    fun higherPriorityPinnedRoutesAreNarrowerWithoutBecomingUnsafe() {
        val widths = List(5) { priority -> pinnedRouteStrokeWidth(priority, 5) }

        assertEquals(3f, widths.first(), 0.001f)
        assertEquals(9f, widths.last(), 0.001f)
        assertTrue(widths.zipWithNext().all { (higher, lower) -> higher < lower })
        assertTrue(widths.all { it >= 3f })
        assertEquals(4f, pinnedRouteStrokeWidth(0, 1), 0.001f)
    }
}
