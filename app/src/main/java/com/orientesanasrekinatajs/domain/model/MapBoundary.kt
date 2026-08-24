package com.orientesanasrekinatajs.domain.model

/**
 * The four corners of a (possibly perspective-distorted) map within a source photo.
 *
 * Corner order is fixed so that both automatic detection and the manual corner-drag UI can
 * produce a consistent result, and so that a perspective transform can be derived from them.
 *
 * @property topLeft     Top-left corner of the map.
 * @property topRight    Top-right corner of the map.
 * @property bottomRight Bottom-right corner of the map.
 * @property bottomLeft  Bottom-left corner of the map.
 */
data class MapBoundary(
    val topLeft: Point2D,
    val topRight: Point2D,
    val bottomRight: Point2D,
    val bottomLeft: Point2D,
) {

    /**
     * Flattens the four corners into the 8-element float array expected by OpenCV's
     * `getPerspectiveTransform` / `warpPerspective` (x0, y0, x1, y1, x2, y2, x3, y3).
     */
    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y,
    )

    /**
     * Returns the four corners as a list, in top-left -> top-right -> bottom-right ->
     * bottom-left order.
     */
    fun corners(): List<Point2D> = listOf(topLeft, topRight, bottomRight, bottomLeft)
}
