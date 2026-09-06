package com.orientesanasrekinatajs.ui.components

import androidx.compose.ui.geometry.Offset

/** A quick swipe pans; only a stationary hold can turn into a marker drag. */
internal class MarkerDragHold(
    private val downTime: Long,
    private val downPosition: Offset,
    private val touchSlop: Float,
) {
    var cancelled = false
        private set
    var activated = false
        private set

    fun update(time: Long, position: Offset): Boolean {
        if (cancelled || activated) return activated
        if (time - downTime >= HOLD_DURATION_MS) {
            activated = true
        } else if ((position - downPosition).getDistance() > touchSlop) {
            cancelled = true
        }
        return activated
    }

    companion object {
        const val HOLD_DURATION_MS = 350L
    }
}
