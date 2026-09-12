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

    @Test fun moderateShortSwipesStayAtTheNearestAnchor() {
        assertEquals(2, settledPanelIndex(heights, 2, 960f, 40f, 500f, 1f))
        assertEquals(0, settledPanelIndex(heights, 0, 200f, -40f, -500f, 1f))
    }

    @Test fun deliberateShortFlickAdvancesOneAnchor() {
        assertEquals(1, settledPanelIndex(heights, 2, 960f, 40f, 1500f, 1f))
        assertEquals(1, settledPanelIndex(heights, 0, 200f, -40f, -1500f, 1f))
        assertEquals(2, settledPanelIndex(heights, 2, 997f, 3f, 1500f, 1f))
    }
}
