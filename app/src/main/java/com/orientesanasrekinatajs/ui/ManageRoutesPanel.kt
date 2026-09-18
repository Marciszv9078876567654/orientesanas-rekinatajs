package com.orientesanasrekinatajs.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orientesanasrekinatajs.ui.components.RouteEditorPanelState
import com.orientesanasrekinatajs.ui.components.settledPanelIndex
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import kotlin.math.roundToInt

/** Only the header resizes the panel; scrolling rows never moves its viewport. */
@Composable
internal fun ManageRoutesPanel(
    panelState: RouteEditorPanelState,
    onPanelStateChange: (RouteEditorPanelState) -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean,
    collapsedHeightPx: Float,
    closing: Boolean,
    onClosed: () -> Unit,
    content: @Composable (headerDragModifier: Modifier, showList: Boolean) -> Unit,
) {
    val animationsEnabled = LocalAnimationsEnabled.current
        // Panel anchors belong to the screen, not to the keyboard's animated viewport.
        BoxWithConstraints(modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val maximum = constraints.maxHeight.toFloat()
            val minimum = kotlin.math.ceil(collapsedHeightPx).coerceIn(1f, maximum)
            val half = (maximum * 0.5f).coerceIn(minimum, maximum)
            val target = when (panelState) {
                RouteEditorPanelState.MINIMIZED -> minimum
                RouteEditorPanelState.HALF -> half
                RouteEditorPanelState.EXPANDED -> maximum
            }
            var height by remember { mutableFloatStateOf(0f) }
            var dragging by remember { mutableStateOf(false) }
            var distance by remember { mutableFloatStateOf(0f) }
            var settleRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(panelState, minimum, maximum, dragging, settleRequest, animationsEnabled) {
                if (dragging) return@LaunchedEffect
                if (animationsEnabled) animate(height, target, animationSpec = com.orientesanasrekinatajs.ui.components.routePanelMotion()) { value, _ -> height = value }
                else height = target
            }
            val dragState = rememberDraggableState { delta ->
                distance += delta
                height = (height - delta).coerceIn(minimum, maximum)
            }
            val imeInsets = WindowInsets.ime
            var consumedBottom by remember { mutableIntStateOf(0) }
            // Read the same live inset in layout and drawing, without a second animation
            // or an inset-dependent composition between keyboard motion and panel placement.
            fun keyboardOverlap() = (imeInsets.getBottom(density) - consumedBottom).coerceAtLeast(0)
            val panelShape = object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(
                    size: androidx.compose.ui.geometry.Size,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    density: androidx.compose.ui.unit.Density,
                ): androidx.compose.ui.graphics.Outline {
                    val available = (maximum - keyboardOverlap()).coerceAtLeast(0f)
                    val top = (available - height).coerceAtLeast(0f)
                    val rounding = if (maximum > half) (top / (maximum - half)).coerceIn(0f, 1f) else 0f
                    return RoundedCornerShape(topStart = 28.dp * rounding, topEnd = 28.dp * rounding)
                        .createOutline(size, layoutDirection, density)
                }
            }
            Box(Modifier.fillMaxSize()
                .onConsumedWindowInsetsChanged { consumedBottom = it.getBottom(density) }
                .imePadding()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .layout { measurable, constraints ->
                        val pixels = keyboardAdjustedPanelHeight(
                            height, constraints.maxHeight.toFloat(),
                        ).roundToInt()
                        val contentPixels = maxOf(pixels, minimum.roundToInt()).coerceAtMost(constraints.maxHeight)
                        val placeable = measurable.measure(constraints.copy(minHeight = contentPixels, maxHeight = contentPixels))
                        layout(placeable.width, pixels) { placeable.placeRelative(0, 0) }
                    }.then(com.orientesanasrekinatajs.ui.components.panelExitModifier(closing, onClosed)).testTag("manage_routes_panel"),
                shape = panelShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                content(Modifier.draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = gesturesEnabled && !closing,
                    onDragStarted = { dragging = true; distance = 0f },
                    onDragStopped = { velocity ->
                        val states = listOf(RouteEditorPanelState.MINIMIZED, RouteEditorPanelState.HALF, RouteEditorPanelState.EXPANDED)
                        onPanelStateChange(states[settledPanelIndex(listOf(minimum, half, maximum), states.indexOf(panelState),
                            height, distance, velocity, density.density)])
                        dragging = false
                        settleRequest++
                    },
                ), height > minimum + with(density) { 12.dp.toPx() })
            }
            }
        }
}

/** Lift the panel with the keyboard; resize only when the remaining viewport is smaller. */
internal fun keyboardAdjustedPanelHeight(
    panelHeight: Float,
    availableHeight: Float,
): Float = panelHeight.coerceIn(0f, availableHeight)