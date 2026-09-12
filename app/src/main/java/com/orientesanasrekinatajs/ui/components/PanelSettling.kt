package com.orientesanasrekinatajs.ui.components

import kotlin.math.abs

/** Long drags settle where released; only deliberate short flicks advance an anchor. */
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
    val shortFlick = abs(dragDistance) in (12f * density)..(56f * density) &&
        abs(velocity) >= 1200f * density
    return if (shortFlick) {
        (currentIndex + if (velocity < 0f) 1 else -1).coerceIn(heights.indices)
    } else nearest
}
