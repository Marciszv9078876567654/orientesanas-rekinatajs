package com.orientesanasrekinatajs.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
        BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
            val density = LocalDensity.current
            val maximum = constraints.maxHeight.toFloat()
            val minimum = kotlin.math.ceil(collapsedHeightPx).coerceIn(1f, maximum)
            val half = (maximum * 0.5f).coerceIn(minimum, maximum)
            val target = when (panelState) {
                RouteEditorPanelState.MINIMIZED -> minimum
                RouteEditorPanelState.HALF -> half
                RouteEditorPanelState.EXPANDED -> maximum
            }
            var height by remember(maximum) { mutableFloatStateOf(0f) }
            var dragging by remember { mutableStateOf(false) }
            var distance by remember { mutableFloatStateOf(0f) }
            var settleRequest by remember { mutableIntStateOf(0) }
            LaunchedEffect(panelState, minimum, maximum, dragging, settleRequest, animationsEnabled) {
                if (dragging) return@LaunchedEffect
                if (animationsEnabled) animate(height, target, animationSpec = tween(180)) { value, _ -> height = value }
                else height = target
            }
            val dragState = rememberDraggableState { delta ->
                distance += delta
                height = (height - delta).coerceIn(minimum, maximum)
            }
            val progress = if (maximum > half) ((height - half) / (maximum - half)).coerceIn(0f, 1f) else 1f
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .layout { measurable, constraints ->
                        val pixels = height.roundToInt().coerceIn(0, constraints.maxHeight)
                        val contentPixels = maxOf(pixels, minimum.roundToInt())
                        val placeable = measurable.measure(constraints.copy(minHeight = contentPixels, maxHeight = contentPixels))
                        layout(placeable.width, pixels) { placeable.placeRelative(0, 0) }
                    }.then(com.orientesanasrekinatajs.ui.components.panelExitModifier(closing, onClosed)).testTag("manage_routes_panel"),
                shape = RoundedCornerShape(topStart = 28.dp * (1f - progress), topEnd = 28.dp * (1f - progress)),
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
