package com.orientesanasrekinatajs.imageprocessing

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrUtilsTest {

    @Test
    fun controlNumbersAcceptOnlyStandaloneTwoOrThreeDigitValues() {
        assertEquals(
            listOf(31, 65, 101),
            OcrUtils.controlNumbersIn("7 31 control-65 101 1234"),
        )
    }

    @Test
    fun controlNumbersIgnoreDigitsEmbeddedInWords() {
        assertEquals(emptyList<Int>(), OcrUtils.controlNumbersIn("A65 B101C"))
    }
}
