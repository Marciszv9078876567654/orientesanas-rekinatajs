package com.orientesanasrekinatajs.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.ControlPointType
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenCVUtilsTest {

    @Test
    fun detectBoundariesReturnsOrderedCorners() {
        val expected = MapBoundary(
            topLeft = Point2D(100f, 80f),
            topRight = Point2D(700f, 120f),
            bottomRight = Point2D(650f, 520f),
            bottomLeft = Point2D(130f, 500f),
        )
        val bitmap = quadrilateralBitmap(expected)

        val detected = OpenCVUtils.detectBoundaries(bitmap)

        assertNotNull(detected)
        assertNear(expected.topLeft, detected!!.topLeft)
        assertNear(expected.topRight, detected.topRight)
        assertNear(expected.bottomRight, detected.bottomRight)
        assertNear(expected.bottomLeft, detected.bottomLeft)
    }

    @Test
    fun detectBoundariesReturnsNullWithoutQuadrilateral() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        assertNull(OpenCVUtils.detectBoundaries(bitmap))
    }

    @Test
    fun warpPerspectiveUsesLongestOpposingEdges() {
        val boundary = MapBoundary(
            topLeft = Point2D(100f, 80f),
            topRight = Point2D(700f, 120f),
            bottomRight = Point2D(650f, 520f),
            bottomLeft = Point2D(130f, 500f),
        )
        val bitmap = quadrilateralBitmap(boundary)

        val warped = OpenCVUtils.warpPerspective(bitmap, boundary)

        assertEquals(601, warped.width)
        assertEquals(421, warped.height)
        assertTrue(Color.luminance(warped.getPixel(warped.width / 2, warped.height / 2)) > 0.9f)
    }

    @Test
    fun detectControlPointsFindsCircleTriangleAndDoubleCircleOnceEach() {
        val bitmap = Bitmap.createBitmap(520, 260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.MAGENTA
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }

        canvas.drawCircle(90f, 120f, 30f, paint)

        val triangle = Path().apply {
            moveTo(260f, 80f)
            lineTo(220f, 155f)
            lineTo(300f, 155f)
            close()
        }
        canvas.drawPath(triangle, paint)

        canvas.drawCircle(430f, 120f, 35f, paint)
        canvas.drawCircle(430f, 120f, 24f, paint)

        val centers = OpenCVUtils.detectControlPoints(bitmap)
        val symbols = OpenCVUtils.detectControlSymbols(bitmap)

        assertEquals(centers.toString(), 3, centers.size)
        assertTrue(centers.any { it.distanceTo(Point2D(90f, 120f)) < 4f })
        assertTrue(centers.any { it.distanceTo(Point2D(260f, 130f)) < 8f })
        assertTrue(centers.any { it.distanceTo(Point2D(430f, 120f)) < 4f })
        assertEquals(
            symbols.toString(),
            setOf(ControlPointType.CONTROL, ControlPointType.START, ControlPointType.FINISH),
            symbols.map { it.type }.toSet(),
        )
    }

    @Test
    fun strictRingDetectionAcceptsAnnulusAndRejectsFilledCircle() {
        val bitmap = Bitmap.createBitmap(360, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.MAGENTA
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawCircle(90f, 90f, 30f, stroke)
        canvas.drawCircle(
            270f,
            90f,
            30f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.MAGENTA },
        )

        val symbols = OpenCVUtils.detectControlSymbols(bitmap, requireRingHole = true)

        assertEquals(symbols.toString(), 1, symbols.size)
        assertTrue(symbols.single().center.distanceTo(Point2D(90f, 90f)) < 4f)
    }

    @Test
    fun hierarchyFilteringRejectsThickDigitLoopsBesideARealRing() {
        val bitmap = Bitmap.createBitmap(420, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawCircle(
            90f,
            100f,
            30f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.MAGENTA
                style = Paint.Style.STROKE
                strokeWidth = 8f
            },
        )
        canvas.drawText(
            "8",
            245f,
            135f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.MAGENTA
                textSize = 100f
                style = Paint.Style.FILL
            },
        )

        val symbols = OpenCVUtils.detectControlSymbols(bitmap)

        assertEquals(symbols.toString(), 1, symbols.size)
        assertTrue(symbols.single().center.distanceTo(Point2D(90f, 100f)) < 4f)
    }

    @Test
    fun detectionKeepsInterruptedRingWhenIntactRingEstablishesItsSize() {
        val bitmap = Bitmap.createBitmap(420, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.MAGENTA
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(90f, 100f, 30f, stroke)
        canvas.drawArc(RectF(240f, 70f, 300f, 130f), 25f, 305f, false, stroke)

        val symbols = OpenCVUtils.detectControlSymbols(bitmap)

        assertEquals(symbols.toString(), 2, symbols.size)
        assertTrue(symbols.any { it.center.distanceTo(Point2D(270f, 100f)) < 5f })
    }

    @Test
    fun detectionEmitsAtMostOneStartFromMultipleTriangularContours() {
        val bitmap = Bitmap.createBitmap(600, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.MAGENTA
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawCircle(70f, 120f, 30f, stroke)
        listOf(220f, 370f, 520f).forEach { centerX ->
            canvas.drawPath(
                Path().apply {
                    moveTo(centerX, 84f)
                    lineTo(centerX - 32f, 140f)
                    lineTo(centerX + 32f, 140f)
                    close()
                },
                stroke,
            )
        }

        val symbols = OpenCVUtils.detectControlSymbols(bitmap)

        assertEquals(1, symbols.count { it.type == ControlPointType.START })
    }

    @Test
    fun sampleControlPointColorFindsKnownRingColorAndRadius() {
        val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawCircle(
            110f,
            110f,
            30f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.MAGENTA
                style = Paint.Style.STROKE
                strokeWidth = 8f
            },
        )

        val sample = OpenCVUtils.sampleControlPointColor(bitmap, Point2D(110f, 110f))

        assertNotNull(sample)
        assertTrue(sample!!.hueRange.contains(150.0))
        assertTrue(sample.saturationRange.contains(255.0))
        assertTrue(sample.valueRange.contains(255.0))
        assertTrue(sample.toString(), abs(sample.estimatedRadius - 34f) < 4f)
    }

    @Test
    fun sampleControlPointColorRejectsNonCircularArea() {
        val bitmap = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(
            30f,
            100f,
            190f,
            115f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.MAGENTA },
        )

        assertNull(OpenCVUtils.sampleControlPointColor(bitmap, Point2D(110f, 108f)))
    }

    @Test
    fun isolateInkColorKeepsMagentaAndRemovesBlackPixels() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(20f, 20f, 80f, 80f, Paint().apply { color = Color.MAGENTA })
        canvas.drawRect(120f, 20f, 180f, 80f, Paint().apply { color = Color.BLACK })

        val isolated = OpenCVUtils.isolateInkColor(bitmap)

        assertEquals(Color.BLACK, isolated.getPixel(50, 50))
        assertEquals(Color.WHITE, isolated.getPixel(150, 50))
    }

    @Test
    fun reprojectPointsMovesIncludedPointsAndDropsPointsOutsideNewBoundary() {
        val oldBoundary = MapBoundary(
            Point2D(0f, 0f), Point2D(100f, 0f), Point2D(100f, 100f), Point2D(0f, 100f),
        )
        val newBoundary = MapBoundary(
            Point2D(25f, 0f), Point2D(100f, 0f), Point2D(100f, 100f), Point2D(25f, 100f),
        )

        val projected = OpenCVUtils.reprojectPoints(
            listOf(Point2D(50f, 50f), Point2D(10f, 50f)),
            oldBoundary,
            newBoundary,
        )

        assertNotNull(projected[0])
        assertTrue(projected[0]!!.x in 23f..27f)
        assertNull(projected[1])
    }

    private fun quadrilateralBitmap(boundary: MapBoundary): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val path = Path().apply {
            moveTo(boundary.topLeft.x, boundary.topLeft.y)
            lineTo(boundary.topRight.x, boundary.topRight.y)
            lineTo(boundary.bottomRight.x, boundary.bottomRight.y)
            lineTo(boundary.bottomLeft.x, boundary.bottomLeft.y)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return bitmap
    }

    private fun assertNear(expected: Point2D, actual: Point2D, tolerance: Float = 4f) {
        assertTrue("Expected x=${expected.x}, actual x=${actual.x}", abs(expected.x - actual.x) <= tolerance)
        assertTrue("Expected y=${expected.y}, actual y=${actual.y}", abs(expected.y - actual.y) <= tolerance)
    }
}
