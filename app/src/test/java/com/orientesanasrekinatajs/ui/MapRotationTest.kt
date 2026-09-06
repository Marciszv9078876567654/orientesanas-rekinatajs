package com.orientesanasrekinatajs.ui

import androidx.compose.ui.geometry.Size
import com.orientesanasrekinatajs.ui.components.interpolatedCardinalFitScale
import com.orientesanasrekinatajs.ui.components.pinnedRouteStrokeWidth
import com.orientesanasrekinatajs.ui.components.controlMarkerScale
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
    fun mapFitInterpolatesDirectlyBetweenCardinalScales() {
        val landscapeScale = interpolatedCardinalFitScale(Size(400f, 700f), 800, 400, 0f)
        val halfwayScale = interpolatedCardinalFitScale(Size(400f, 700f), 800, 400, 0.5f)
        val rotatedScale = interpolatedCardinalFitScale(Size(400f, 700f), 800, 400, 1f)

        assertEquals(0.5f, landscapeScale, 0.001f)
        assertEquals(0.6875f, halfwayScale, 0.001f)
        assertEquals(0.875f, rotatedScale, 0.001f)
        assertTrue(halfwayScale in landscapeScale..rotatedScale)
    }

    @Test
    fun quarterTurnAnimationStartsAtTheCurrentManualAngle() {
        val offset = rotationOffsetPreservingAngle(
            currentQuarterTurns = 0,
            currentOffsetDegrees = 30f,
            targetQuarterTurns = 1,
        )

        assertEquals(-60f, offset, 0.001f)
        assertEquals(30f, 90f + offset, 0.001f)
    }

    @Test
    fun higherPriorityPinnedRoutesAreNarrowerWithoutBecomingUnsafe() {
        val widths = List(5) { priority -> pinnedRouteStrokeWidth(priority, 5) }

        assertEquals(3f, widths.first(), 0.001f)
        assertEquals(7f, widths.last(), 0.001f)
        assertTrue(widths.zipWithNext().all { (higher, lower) -> higher < lower })
        assertTrue(widths.all { it in 3f..7f })
        assertEquals(4f, pinnedRouteStrokeWidth(0, 1), 0.001f)
    }

    @Test
    fun controlMarkersKeepGrowingThroughoutTheZoomRange() {
        assertEquals(1f, controlMarkerScale(1f), 0.001f)
        assertEquals(2f, controlMarkerScale(4f), 0.001f)
        assertTrue(listOf(1f, 1.5f, 2f, 4f, 8f, 12f).map(::controlMarkerScale)
            .zipWithNext().all { (before, after) -> after > before })
    }
}
