package com.orientesanasrekinatajs.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orientesanasrekinatajs.domain.model.Point2D
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5ImageUtilsTest {

    @Test
    fun cropRegionOfInterestKeepsExtraMarginAroundControl() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)

        val cropped = ImageCropUtils.cropRegionOfInterest(
            bitmap = bitmap,
            center = Point2D(200f, 150f),
            radius = 20f,
        )

        assertEquals(130, cropped.width)
        assertEquals(130, cropped.height)
    }

    @Test
    fun cropRegionOfInterestClipsAtBitmapEdges() {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)

        val cropped = ImageCropUtils.cropRegionOfInterest(
            bitmap = bitmap,
            center = Point2D(10f, 10f),
            radius = 20f,
        )

        assertEquals(75, cropped.width)
        assertEquals(75, cropped.height)
    }

    @Test
    fun cropRegionOfInterestExpandsSmallEdgeCropForMlKit() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val cropped = ImageCropUtils.cropRegionOfInterest(
            bitmap = bitmap,
            center = Point2D(1f, 1f),
            radius = 4f,
        )

        assertEquals(32, cropped.width)
        assertEquals(32, cropped.height)
    }

    @Test
    fun cropRegionOfInterestRejectsInvalidRadius() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        assertThrows(IllegalArgumentException::class.java) {
            ImageCropUtils.cropRegionOfInterest(bitmap, Point2D(50f, 50f), 0f)
        }
    }

    @Test
    fun extractControlNumberRecognizesSyntheticText() = runBlocking {
        val bitmap = textBitmap("65")

        assertEquals(65, OcrUtils.extractControlNumber(bitmap))
    }

    @Test
    fun extractControlNumberUpscalesSmallMapLabel() = runBlocking {
        val bitmap = textBitmap("83", width = 64, height = 64, textSize = 24f)

        assertEquals(83, OcrUtils.extractControlNumber(bitmap))
    }

    @Test
    fun extractControlNumberRejectsSingleDigitText() = runBlocking {
        val bitmap = textBitmap("7")

        assertNull(OcrUtils.extractControlNumber(bitmap))
    }

    @Test
    fun controlNumberParserAcceptsDigitsSplitByOcrSpacing() {
        assertEquals(listOf(83, 101), OcrUtils.controlNumbersIn("8 3   1 0 1"))
    }

    @Test
    fun controlNumberParserCorrectsCommonOcrGlyphConfusions() {
        assertEquals(listOf(83, 65, 101), OcrUtils.controlNumbersIn("B3 6S I0I"))
    }

    @Test
    fun extractControlNumberSkipsBitmapBelowMlKitMinimum() = runBlocking {
        val bitmap = Bitmap.createBitmap(31, 31, Bitmap.Config.ARGB_8888)

        assertNull(OcrUtils.extractControlNumber(bitmap))
    }

    private fun textBitmap(
        value: String,
        width: Int = 480,
        height: Int = 240,
        textSize: Float = 160f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = height / 2f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(value, width / 2f, baseline, paint)
        return bitmap
    }
}
