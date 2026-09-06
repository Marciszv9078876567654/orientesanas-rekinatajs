package com.orientesanasrekinatajs.ui

import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.nextRouteColorIndex
import com.orientesanasrekinatajs.ui.components.routeColor
import org.junit.Assert.*
import org.junit.Test

class ControlAndRouteColorTest {
    @Test
    fun acceptsLowScoringControlsAndThirty() {
        listOf(10 to 1, 19 to 1, 20 to 2, 29 to 2, 30 to 3, 300 to 30, 309 to 30).forEach { (code, score) ->
            assertTrue(isValidControlCode(code.toString()))
            assertEquals(score, ControlPoint(code = code, center = Point2D(0f, 0f)).points)
        }
        listOf("", "0", "9", "-1", "1000", "abc").forEach { assertFalse(isValidControlCode(it)) }
    }

    @Test
    fun routesCycleThroughTheEntireExpandedPaletteRepeatedly() {
        val metadata = mutableListOf<RouteMetadata>()
        repeat(48) { index ->
            val next = nextRouteColorIndex(metadata)
            assertEquals(index, next)
            assertEquals(routeColor(index % 16), routeColor(next))
            metadata += RouteMetadata(colorIndex = next)
        }
        assertEquals(16, (0 until 16).map(::routeColor).distinct().size)
        assertNotEquals(routeColor(16), routeColor(17))
    }
}
