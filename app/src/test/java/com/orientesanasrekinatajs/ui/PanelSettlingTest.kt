package com.orientesanasrekinatajs.ui

import com.orientesanasrekinatajs.ui.components.settledPanelIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSettlingTest {
    private val heights = listOf(160f, 500f, 1000f)

    @Test fun longDragsReachEitherExtremeRegardlessOfReleaseSpeed() {
        assertEquals(0, settledPanelIndex(heights, 2, 170f, 830f, 1800f, 1f))
        assertEquals(2, settledPanelIndex(heights, 0, 980f, -820f, -1800f, 1f))
        assertEquals(0, settledPanelIndex(heights, 2, 170f, 830f, 500f, 1f))
    }

    @Test fun slowShortDragsStayAtTheNearestAnchor() {
        assertEquals(2, settledPanelIndex(heights, 2, 960f, 40f, 150f, 1f))
        assertEquals(0, settledPanelIndex(heights, 0, 200f, -40f, -150f, 1f))
    }

    @Test fun deliberateShortFlickAdvancesOneAnchor() {
        assertEquals(1, settledPanelIndex(heights, 2, 960f, 40f, 1500f, 1f))
        assertEquals(1, settledPanelIndex(heights, 0, 200f, -40f, -1500f, 1f))
        assertEquals(2, settledPanelIndex(heights, 2, 997f, 3f, 1500f, 1f))
    }

    @Test fun smallFlicksAdvanceWithoutRequiringHighSpeed() {
        assertEquals(1, settledPanelIndex(heights, 2, 990f, 10f, 500f, 1f))
        assertEquals(1, settledPanelIndex(heights, 0, 170f, -10f, -500f, 1f))
        assertEquals(0, settledPanelIndex(heights, 1, 490f, 10f, 500f, 1f))
        assertEquals(2, settledPanelIndex(heights, 1, 510f, -10f, -500f, 1f))
    }

    @Test fun flicksBeyondTheOldDistanceLimitStillAdvance() {
        assertEquals(1, settledPanelIndex(heights, 2, 920f, 80f, 500f, 1f))
        assertEquals(1, settledPanelIndex(heights, 0, 240f, -80f, -500f, 1f))
    }

    @Test fun flickThresholdsScaleWithDisplayDensity() {
        val scaled = heights.map { it * 3f }
        assertEquals(1, settledPanelIndex(scaled, 0, 510f, -30f, -1500f, 3f))
        assertEquals(0, settledPanelIndex(scaled, 0, 510f, -30f, -450f, 3f))
    }

    @Test fun reversingAtReleaseDoesNotSendPanelInTheOppositeDirection() {
        assertEquals(1, settledPanelIndex(heights, 1, 540f, -40f, 500f, 1f))
        assertEquals(1, settledPanelIndex(heights, 1, 460f, 40f, -500f, 1f))
    }
}
