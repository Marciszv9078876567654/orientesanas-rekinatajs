package com.orientesanasrekinatajs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ManageRoutesKeyboardHeightTest {
    @Test fun fullscreenTracksKeyboardWithoutChangingItsTop() {
        for (available in listOf(1000f, 900f, 600f, 900f, 1000f)) {
            assertEquals(available, keyboardAdjustedPanelHeight(1000f, available), 0.01f)
        }
    }

    @Test fun halfHeightStaysIntactThroughoutKeyboardOpeningAndClosing() {
        assertEquals(500f, keyboardAdjustedPanelHeight(500f, 900f), 0.01f)
        assertEquals(500f, keyboardAdjustedPanelHeight(500f, 600f), 0.01f)
        assertEquals(500f, keyboardAdjustedPanelHeight(500f, 900f), 0.01f)
        assertEquals(500f, keyboardAdjustedPanelHeight(500f, 1000f), 0.01f)
    }

    @Test fun openingStillStartsAtZeroAndSmallViewportsAreClamped() {
        assertEquals(0f, keyboardAdjustedPanelHeight(0f, 600f), 0.01f)
        assertEquals(100f, keyboardAdjustedPanelHeight(500f, 100f), 0.01f)
    }
}
