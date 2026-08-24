package com.orientesanasrekinatajs.domain.model

import kotlin.math.hypot

/**
 * A two-dimensional point, used to express geometry in the (rectified) map's pixel space.
 *
 * This is a pure Kotlin type with no Android dependencies.
 *
 * @property x Horizontal coordinate.
 * @property y Vertical coordinate.
 */
data class Point2D(val x: Float, val y: Float) {

    /**
     * Euclidean distance from this point to [other].
     *
     * Uses [kotlin.math.hypot], which is numerically stable against overflow/underflow
     * and always returns a non-negative value.
     */
    fun distanceTo(other: Point2D): Float = hypot(x - other.x, y - other.y)

    /**
     * Squared Euclidean distance from this point to [other].
     *
     * Provided for performance-sensitive code (e.g. nearest-neighbor lookups) that only needs
     * to compare relative distances and therefore can skip the comparatively expensive square
     * root. It is always non-negative and monotonic with respect to [distanceTo].
     */
    fun squaredDistanceTo(other: Point2D): Float {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }

    /**
     * The midpoint between this point and [other].
     */
    fun midpoint(other: Point2D): Point2D = Point2D((x + other.x) / 2f, (y + other.y) / 2f)
}
