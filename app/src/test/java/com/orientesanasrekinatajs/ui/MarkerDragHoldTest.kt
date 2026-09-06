package com.orientesanasrekinatajs.ui

import androidx.compose.ui.geometry.Offset
import com.orientesanasrekinatajs.ui.components.MarkerDragHold
import org.junit.Assert.*
import org.junit.Test

class MarkerDragHoldTest {
    @Test
    fun holdIsRequiredBeforeMovingAMarker() {
        val hold = MarkerDragHold(1000L, Offset.Zero, 10f)
        assertFalse(hold.update(1200L, Offset(2f, 2f)))
        assertFalse(hold.update(1349L, Offset(1f, 1f)))
        assertTrue(hold.update(1350L, Offset(30f, 20f)))
        assertTrue(hold.update(1500L, Offset(100f, 100f)))
    }

    @Test
    fun quickSwipeCannotBecomeADragLaterInTheSameGesture() {
        val hold = MarkerDragHold(0L, Offset.Zero, 10f)
        assertFalse(hold.update(60L, Offset(20f, 0f)))
        assertTrue(hold.cancelled)
        assertFalse(hold.update(500L, Offset.Zero))
        assertFalse(hold.activated)
    }

    @Test
    fun smallFingerJitterDoesNotCancelAHold() {
        val hold = MarkerDragHold(0L, Offset(50f, 50f), 10f)
        repeat(10) { assertFalse(hold.update(it * 30L, Offset(52f, 49f))) }
        assertTrue(hold.update(400L, Offset(55f, 50f)))
    }
}
