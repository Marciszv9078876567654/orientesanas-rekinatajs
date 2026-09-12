package com.orientesanasrekinatajs.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.ui.ConfirmationDialog
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import com.orientesanasrekinatajs.ui.theme.LocalLongPressFeedback
import kotlinx.coroutines.delay
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import kotlin.math.abs
import kotlin.math.roundToInt

private data class ReleasedPoint(val point: ControlPoint, val top: Float)

/** A drag previews an insertion without changing the list until the finger is released. */
@Composable
internal fun RouteEditorPointList(
    path: List<ControlPoint>,
    onPathChange: (List<ControlPoint>) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val animationsEnabled = LocalAnimationsEnabled.current
    val latestLongPressFeedback by rememberUpdatedState(LocalLongPressFeedback.current)
    var pendingDelete by remember { mutableStateOf<ControlPoint?>(null) }
    var movedPointId by remember { mutableStateOf<String?>(null) }
    var moveRequest by remember { mutableIntStateOf(0) }
    var followOffset by remember { mutableStateOf<Int?>(null) }
    val moveHighlight = remember { Animatable(0f) }
    val latestPath by rememberUpdatedState(path)
    val latestOnPathChange by rememberUpdatedState(onPathChange)
    val latestOnDraggingChange by rememberUpdatedState(onDraggingChange)
    val handles = remember { mutableMapOf<String, LayoutCoordinates>() }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var draggedPoint by remember { mutableStateOf<ControlPoint?>(null) }
    var releasedPoint by remember { mutableStateOf<ReleasedPoint?>(null) }
    val releaseProgress = remember(releasedPoint) { Animatable(0f) }
    var dragStartPath by remember { mutableStateOf(emptyList<ControlPoint>()) }
    var centerY by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val edgePx = with(density) { 64.dp.toPx() }
    val maxSpeedPx = with(density) { 600.dp.toPx() }
    val sourceIndex = path.indexOfFirst { it.id == draggedPoint?.id }
    LaunchedEffect(releasedPoint) {
        if (releasedPoint == null) return@LaunchedEffect
        releaseProgress.snapTo(0f)
        // Let the reordered list lay out its final slot before the floating row settles.
        withFrameNanos { }
        withFrameNanos { }
        releaseProgress.animateTo(1f, tween(220))
        releasedPoint = null
    }
    fun movePoint(from: Int, to: Int, followRow: Boolean = false) {
        if (from !in latestPath.indices || to !in latestPath.indices || from == to) return
        followOffset = if (followRow) {
            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == from }?.offset
        } else null
        movedPointId = latestPath[from].id
        latestOnPathChange(latestPath.toMutableList().apply { add(to, removeAt(from)) })
        moveRequest++
    }
    LaunchedEffect(moveRequest) {
        if (moveRequest == 0) return@LaunchedEffect
        moveHighlight.snapTo(0.65f)
        withFrameNanos { }
        val index = latestPath.indexOfFirst { it.id == movedPointId }
        if (index >= 0) {
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                if (animationsEnabled) listState.animateScrollToItem(index) else listState.scrollToItem(index)
            } else {
                val scroll = when {
                    followOffset != null -> item.offset - requireNotNull(followOffset)
                    item.offset < layout.viewportStartOffset -> item.offset - layout.viewportStartOffset
                    item.offset + item.size > layout.viewportEndOffset -> item.offset + item.size - layout.viewportEndOffset
                    else -> 0
                }
                if (scroll != 0) {
                    if (animationsEnabled) listState.animateScrollBy(scroll.toFloat(), tween(220))
                    else listState.scrollBy(scroll.toFloat())
                }
            }
        }
        delay(300)
        if (animationsEnabled) moveHighlight.animateTo(0f, tween(700)) else moveHighlight.snapTo(0f)
        movedPointId = null
    }
    val destination by remember {
        derivedStateOf {
            val controls = latestPath.indices.filter { latestPath[it].type == ControlPointType.CONTROL }
            if (draggedPoint == null || controls.isEmpty()) null else {
                listState.layoutInfo.visibleItemsInfo.minByOrNull {
                    abs(it.offset + it.size / 2f - centerY)
                }?.index?.coerceIn(controls.first(), controls.last())
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { latestOnDraggingChange(false) }
    }
    // Cancel the preview if another action changes the path while the gesture is active.
    LaunchedEffect(path) {
        if (draggedPoint != null && path != dragStartPath) {
            draggedPoint = null
            latestOnDraggingChange(false)
        }
    }
    LaunchedEffect(draggedPoint?.id) {
        if (draggedPoint == null) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (true) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            val layout = listState.layoutInfo
            val edge = minOf(edgePx, (layout.viewportEndOffset - layout.viewportStartOffset) / 3f)
            val direction = when {
                pointerY < layout.viewportStartOffset + edge ->
                    -((layout.viewportStartOffset + edge - pointerY) / edge).coerceIn(0f, 1f)
                pointerY > layout.viewportEndOffset - edge ->
                    ((pointerY - layout.viewportEndOffset + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (direction != 0f) listState.scrollBy(direction * maxSpeedPx * seconds)
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .testTag("route_editor_headers"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RouteEditorValues(
                stringResource(R.string.route_editor_number),
                stringResource(R.string.route_editor_control),
                stringResource(R.string.route_editor_points),
                modifier = Modifier.weight(1f),
                isHeader = true,
            )
            VerticalDivider(Modifier.height(24.dp))
            Spacer(Modifier.width(152.dp))
        }
        HorizontalDivider()
        Box(
            Modifier.fillMaxWidth().weight(1f).clipToBounds()
                .testTag("route_editor_list")
                .onGloballyPositioned { listCoordinates = it }
                // Own the gesture outside the lazy items so it survives scrolling the source away.
                // Only a press inside an actual handle can start this gesture.
                .pointerInput(listState) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (releasedPoint != null) return@awaitEachGesture
                        val rootPosition = listCoordinates?.localToRoot(down.position)
                            ?: return@awaitEachGesture
                        val id = handles.entries.firstOrNull { (_, coordinates) ->
                            coordinates.isAttached && coordinates.boundsInRoot().contains(rootPosition)
                        }?.key ?: return@awaitEachGesture
                        val point = latestPath.firstOrNull { it.id == id }
                            ?: return@awaitEachGesture
                        val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                            ?: return@awaitEachGesture
                        dragStartPath = latestPath
                        centerY = item.offset + item.size / 2f
                        rowHeight = item.size.toFloat()
                        pointerY = press.position.y
                        draggedPoint = point
                        latestLongPressFeedback()
                        latestOnDraggingChange(true)
                        try {
                            val released = drag(press.id) { change ->
                                centerY += change.positionChange().y
                                pointerY = change.position.y
                                change.consume()
                            }
                            if (released && draggedPoint != null && latestPath == dragStartPath) {
                                val from = latestPath.indexOfFirst { it.id == id }
                                val to = destination
                                if (animationsEnabled && from >= 0 && to != null) {
                                    val maxTop = (listState.layoutInfo.viewportEndOffset - rowHeight).coerceAtLeast(0f)
                                    releasedPoint = ReleasedPoint(point, (centerY - rowHeight / 2f).coerceIn(0f, maxTop))
                                }
                                if (from >= 0 && to != null && to != from) {
                                    movePoint(from, to)
                                }
                            }
                        } finally {
                            draggedPoint = null
                            latestOnDraggingChange(false)
                        }
                    }
                },
        ) {
            LazyColumn(state = listState, userScrollEnabled = draggedPoint == null && releasedPoint == null) {
                itemsIndexed(path, key = { index, point ->
                    if (point.type == ControlPointType.CONTROL) point.id else "$index:${point.id}"
                }) { index, point ->
                    val isControl = point.type == ControlPointType.CONTROL
                    DisposableEffect(point.id) { onDispose { handles.remove(point.id) } }
                    Column(Modifier.animateItem(
                        fadeInSpec = if (animationsEnabled && releasedPoint?.point?.id != point.id) tween(220) else null,
                        fadeOutSpec = if (animationsEnabled) tween(180) else null,
                        placementSpec = if (animationsEnabled && releasedPoint?.point?.id != point.id) tween(280) else null,
                    )) {
                    Row(
                        Modifier.fillMaxWidth().testTag("route_point_${point.id}")
                            .graphicsLayer {
                                alpha = when (point.id) {
                                    releasedPoint?.point?.id -> 0f
                                    draggedPoint?.id -> 0.2f
                                    else -> 1f
                                }
                            }
                            .background(MaterialTheme.colorScheme.primary.copy(
                                alpha = if (movedPointId == point.id) moveHighlight.value else 0f,
                            ), RoundedCornerShape(8.dp))
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RouteEditorPointValues(index, point, Modifier.weight(1f))
                        VerticalDivider(Modifier.height(28.dp))
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { pendingDelete = point },
                                    enabled = isControl && draggedPoint == null && releasedPoint == null,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.remove_route_point))
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = { movePoint(index, index + 1, followRow = true) },
                                    enabled = draggedPoint == null && releasedPoint == null && isControl &&
                                        path.getOrNull(index + 1)?.type == ControlPointType.CONTROL,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_point_down))
                                }
                                IconButton(
                                    onClick = { movePoint(index, index - 1, followRow = true) },
                                    enabled = draggedPoint == null && releasedPoint == null && isControl &&
                                        path.getOrNull(index - 1)?.type == ControlPointType.CONTROL,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_point_up))
                                }
                                Spacer(Modifier.width(8.dp))
                                if (isControl) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        stringResource(R.string.drag_point_to_reorder),
                                        Modifier.size(40.dp).testTag("route_point_drag_${point.id}")
                                            .onGloballyPositioned { handles[point.id] = it }
                                            .background(
                                                if (draggedPoint?.id == point.id) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                                else Color.Transparent,
                                                RoundedCornerShape(20.dp),
                                            ).padding(8.dp),
                                    )
                                } else Spacer(Modifier.width(40.dp))
                            }
                        }
                    }
                    HorizontalDivider()
                    }
                }
            }
            val target = destination?.let { index ->
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            }
            if (target != null && destination != sourceIndex) {
                val insertionY = target.offset + if (target.index > sourceIndex) target.size else 0
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().zIndex(2f).offset { IntOffset(0, insertionY) }
                    .height(3.dp).testTag("route_editor_insertion")) {
                    drawLine(color, Offset(16.dp.toPx(), 0f), Offset(size.width - 16.dp.toPx(), 0f), 3.dp.toPx())
                }
            }
            (draggedPoint ?: releasedPoint?.point)?.let { point ->
                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .offset {
                            val maxTop = (listState.layoutInfo.viewportEndOffset - rowHeight).coerceAtLeast(0f)
                            val released = releasedPoint
                            val top = if (released != null) {
                                val slot = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == point.id }
                                val targetTop = slot?.offset?.toFloat() ?: released.top
                                released.top + (targetTop - released.top) * releaseProgress.value
                            } else (centerY - rowHeight / 2f).coerceIn(0f, maxTop)
                            IntOffset(0, top.roundToInt())
                        }
                        .testTag(if (releasedPoint != null) "route_editor_drop_preview" else "route_editor_drag_preview")
                        .clearAndSetSemantics { },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        Modifier.height(with(density) { rowHeight.toDp() }).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        RouteEditorPointValues(path.indexOfFirst { it.id == point.id }, point, Modifier.weight(1f))
                        VerticalDivider(Modifier.height(28.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            listOf(Icons.Default.Delete, Icons.Default.ArrowDownward, Icons.Default.ArrowUpward).forEachIndexed { index, icon ->
                                Icon(icon, null, Modifier.size(32.dp).padding(4.dp))
                                if (index == 0 || index == 2) Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.Default.DragHandle, null, Modifier.size(40.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                .padding(8.dp))
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { point ->
        ConfirmationDialog(
            title = stringResource(R.string.remove_route_point),
            message = stringResource(R.string.remove_route_point_confirmation, point.code),
            onConfirm = {
                latestOnPathChange(latestPath.filterNot { it.id == point.id && it.type == ControlPointType.CONTROL })
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun RouteEditorPointValues(index: Int, point: ControlPoint, modifier: Modifier) {
    RouteEditorValues(
        (index + 1).toString(),
        when (point.type) {
            ControlPointType.START -> "S"
            ControlPointType.FINISH -> "F"
            ControlPointType.START_FINISH -> "S/F"
            ControlPointType.CONTROL -> point.code.toString()
        },
        if (point.type == ControlPointType.CONTROL) point.points.toString() else "–",
        modifier,
    )
}

@Composable
private fun RouteEditorValues(
    number: String,
    control: String,
    points: String,
    modifier: Modifier,
    isHeader: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        listOf(number, control, points).forEach { value ->
            Text(
                value,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
