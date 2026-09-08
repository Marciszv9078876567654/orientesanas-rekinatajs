package com.orientesanasrekinatajs.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternativeRouteBoundsTest {
    @Test
    fun simultaneousBoundsUseTheStricterLimitOnEachSide() {
        assertEquals(900f to 1100f, intersectAlternativeBounds(800f, 1100f, 900f, 1200f))
        assertEquals(25 to 35, intersectAlternativeBounds(25, 40, 20, 35))
    }

    @Test
    fun DisabledOrEmptyBoundsDoNotConstrainTheOtherLimits() {
        assertEquals(20 to 40, intersectAlternativeBounds(null, null, 20, 40))
        assertEquals(20 to 40, intersectAlternativeBounds(20, 40, null, null))
        assertEquals(null to null, intersectAlternativeBounds<Int>(null, null, null, null))
        assertEquals(20 to 40, intersectAlternativeBounds(20, null, null, 40))
    }

    @Test
    fun ConflictingLimitsRemainInvalidRatherThanSilentlyDroppingAConstraint() {
        val (minimum, maximum) = intersectAlternativeBounds(100f, 200f, 250f, 350f)
        assertTrue(requireNotNull(minimum) > requireNotNull(maximum))
    }
}
