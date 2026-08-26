package com.orientesanasrekinatajs.imageprocessing

import android.graphics.Bitmap
import com.orientesanasrekinatajs.domain.model.Point2D
import kotlin.math.ceil
import kotlin.math.floor

/** Bitmap cropping operations used to prepare small OCR inputs. */
object ImageCropUtils {
    private const val ROI_RADIUS_MULTIPLIER = 2.5f

    /**
     * Crops a square extending 2.5 symbol radii from [center] in every direction.
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
        val left = floor(center.x - halfSize).toInt().coerceAtLeast(0)
        val top = floor(center.y - halfSize).toInt().coerceAtLeast(0)
        val right = ceil(center.x + halfSize).toInt().coerceAtMost(bitmap.width)
        val bottom = ceil(center.y + halfSize).toInt().coerceAtMost(bitmap.height)

        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}
