package com.orientesanasrekinatajs.ui.components

import kotlin.math.abs

/** The release position takes priority; a flick can leave the current anchor before its midpoint. */
internal fun settledPanelIndex(
    heights: List<Float>,
    currentIndex: Int,
    releasedHeight: Float,
    dragDistance: Float,
    velocity: Float,
    density: Float,
): Int {
    val nearest = heights.indices.minBy { abs(heights[it] - releasedHeight) }
    if (nearest != currentIndex) return nearest
    val flick = abs(dragDistance) >= 8f * density &&
        abs(velocity) >= 400f * density && dragDistance * velocity > 0f
    return if (flick) {
        (currentIndex + if (velocity < 0f) 1 else -1).coerceIn(heights.indices)
    } else nearest
}
