package com.orientesanasrekinatajs.imageprocessing

import android.graphics.Bitmap
import com.orientesanasrekinatajs.domain.model.Point2D
import kotlin.math.ceil
import kotlin.math.floor

/** Bitmap cropping operations used to prepare small OCR inputs. */
object ImageCropUtils {
    private const val ROI_RADIUS_MULTIPLIER = 3.25f
    private const val MIN_OCR_DIMENSION = 32

    /**
     * Crops a square extending 3.25 symbol radii from [center] in every direction. The extra
     * margin keeps labels intact when they sit beyond a broken circle's estimated outer edge.
     *
     * The requested rectangle is clipped to the bitmap, which keeps symbols near an image edge
     * valid without padding or out-of-bounds failures.
     */
    fun cropRegionOfInterest(
        bitmap: Bitmap,
        center: Point2D,
        radius: Float,
    ): Bitmap {
        require(radius.isFinite() && radius > 0f) { "Radius must be finite and positive" }
        require(center.x.isFinite() && center.y.isFinite()) { "Center coordinates must be finite" }
        require(center.x >= 0f && center.x < bitmap.width) { "Center x must be inside the bitmap" }
        require(center.y >= 0f && center.y < bitmap.height) { "Center y must be inside the bitmap" }

        val halfSize = radius * ROI_RADIUS_MULTIPLIER
        val requestedLeft = floor(center.x - halfSize).toInt().coerceAtLeast(0)
        val requestedTop = floor(center.y - halfSize).toInt().coerceAtLeast(0)
        val requestedRight = ceil(center.x + halfSize).toInt().coerceAtMost(bitmap.width)
        val requestedBottom = ceil(center.y + halfSize).toInt().coerceAtMost(bitmap.height)
        val (left, right) = expandToMinimumSize(
            requestedLeft,
            requestedRight,
            bitmap.width,
        )
        val (top, bottom) = expandToMinimumSize(
            requestedTop,
            requestedBottom,
            bitmap.height,
        )

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun expandToMinimumSize(start: Int, end: Int, limit: Int): Pair<Int, Int> {
        val currentSize = end - start
        val targetSize = maxOf(currentSize, minOf(MIN_OCR_DIMENSION, limit))
        if (targetSize == currentSize) return start to end

        val extraBefore = (targetSize - currentSize) / 2
        var expandedStart = (start - extraBefore).coerceAtLeast(0)
        var expandedEnd = expandedStart + targetSize
        if (expandedEnd > limit) {
            expandedEnd = limit
            expandedStart = limit - targetSize
        }
        return expandedStart to expandedEnd
    }
}
