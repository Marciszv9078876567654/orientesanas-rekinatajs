package com.orientesanasrekinatajs.domain.routing

import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DistanceMatrixTest {

    @Test
    fun distancesAreSymmetricAndConvertedToMeters() {
        val first = point("first", 0f, 0f)
        val second = point("second", 3f, 4f)
        val matrix = DistanceMatrix(listOf(first, second), pixelsPerMeter = 0.5f)

        assertEquals(0f, matrix[first, first], 0f)
        assertEquals(10f, matrix[first, second], 0.0001f)
        assertEquals(matrix[first, second], matrix[second, first], 0f)
        assertEquals(10f, matrix[0, 1], 0.0001f)
    }

    @Test
    fun invalidScaleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DistanceMatrix(listOf(point("point", 0f, 0f)), pixelsPerMeter = 0f)
        }
    }

    @Test
    fun duplicatePointIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DistanceMatrix(
                listOf(point("duplicate", 0f, 0f), point("duplicate", 1f, 1f)),
                pixelsPerMeter = 1f,
            )
        }
    }

    private fun point(id: String, x: Float, y: Float) = ControlPoint(
        id = id,
        code = 31,
        center = Point2D(x, y),
    )
}
