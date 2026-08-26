package com.orientesanasrekinatajs.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clipToBounds
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/** Displays a map and lets the user drag its four perspective-correction corners. */
@Composable
fun InteractiveCornerCanvas(
    bitmap: Bitmap,
    boundary: MapBoundary,
    onBoundaryChange: (MapBoundary) -> Unit,
    rotationQuarterTurns: Int = 0,
    recenterKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val latestBoundary by rememberUpdatedState(boundary)
    val latestOnBoundaryChange by rememberUpdatedState(onBoundaryChange)
    val lineColor = Color(0xFFFFC107)
    val handleColor = Color(0xFFE91E63)
    val animatedRotation = animatedRotationDegrees(rotationQuarterTurns)
    val animationsEnabled = LocalAnimationsEnabled.current
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(rotationQuarterTurns) {
        zoom = 1f
        pan = Offset.Zero
    }
    LaunchedEffect(recenterKey) {
        if (recenterKey > 0) {
            animateViewportToCenter(zoom, pan, animationsEnabled) { newZoom, newPan ->
                zoom = newZoom
                pan = newPan
            }
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { newSize ->
                pan = stablePanAfterResize(
                    oldSize = viewportSize,
                    newSize = newSize,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    rotationQuarterTurns = rotationQuarterTurns,
                    zoom = zoom,
                    currentPan = pan,
                )
                viewportSize = newSize
            }
            .testTag("cornerCanvas")
            .pointerInput(bitmap.width, bitmap.height, rotationQuarterTurns) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val initialViewport = viewportTransform(
                        size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
                    )
                    var selectedCorner = latestBoundary.corners()
                        .map(initialViewport::toCanvas)
                        .mapIndexed { index, corner -> index to (corner - down.position).getDistance() }
                        .filter { (_, distance) -> distance <= CORNER_TOUCH_RADIUS_PX }
                        .minByOrNull { (_, distance) -> distance }
                        ?.first

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            selectedCorner = null
                            updateViewportGesture(
                                zoomChange = event.calculateZoom(),
                                panChange = event.calculatePan(),
                                centroid = event.calculateCentroid(useCurrent = true),
                                viewportCenter = Offset(size.width / 2f, size.height / 2f),
                                currentZoom = zoom,
                                currentPan = pan,
                                onUpdate = { newZoom, newPan -> zoom = newZoom; pan = newPan },
                            )
                            event.changes.forEach { it.consume() }
                        } else {
                            event.changes.firstOrNull()?.let { change ->
                                if (change.pressed) {
                                    if (selectedCorner != null) {
                                        val viewport = viewportTransform(
                                            size, bitmap.width, bitmap.height,
                                            rotationQuarterTurns, zoom, pan,
                                        )
                                        latestOnBoundaryChange(
                                            latestBoundary.withCorner(
                                                selectedCorner,
                                                viewport.toImage(change.position),
                                            ),
                                        )
                                    } else if (zoom > 1f) {
                                        pan += change.position - change.previousPosition
                                    }
                                    change.consume()
                                }
                            }
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                }
            },
    ) {
        val viewport = viewportTransform(
            size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
            rotationDegrees = animatedRotation,
        )
        withViewport(viewport) {
            drawFittedImage(image, viewport.base)
            val corners = boundary.corners().map(viewport.base::toCanvas)
            corners.forEachIndexed { index, corner ->
                val next = corners[(index + 1) % corners.size]
                drawLine(lineColor, corner, next, strokeWidth = 4f / zoom, cap = StrokeCap.Round)
            }
            corners.forEach { corner ->
                val pointScale = sqrt(zoom)
                drawCircle(Color.White, radius = 15f / pointScale, center = corner)
                drawCircle(handleColor, radius = 11f / pointScale, center = corner)
            }
        }
    }
}

/** Draws an optimized route over the rectified map, animating its path when enabled. */
@Composable
fun RouteRenderingCanvas(
    bitmap: Bitmap,
    route: List<ControlPoint>,
    allPoints: List<ControlPoint> = route,
    rotationQuarterTurns: Int = 0,
    recenterKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val animationsEnabled = LocalAnimationsEnabled.current
    val routeKey = route.joinToString(separator = ":") { it.id }
    val progress = remember { Animatable(if (animationsEnabled) 0f else 1f) }
    val routeColor = Color(0xFFE91E63)
    val animatedRotation = animatedRotationDegrees(rotationQuarterTurns)
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(rotationQuarterTurns) {
        zoom = 1f
        pan = Offset.Zero
    }
    LaunchedEffect(recenterKey) {
        if (recenterKey > 0) {
            animateViewportToCenter(zoom, pan, animationsEnabled) { newZoom, newPan ->
                zoom = newZoom
                pan = newPan
            }
        }
    }

    LaunchedEffect(routeKey, animationsEnabled) {
        if (!animationsEnabled || route.size < 2) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = ((route.size - 1) * 280).coerceAtMost(2_500)),
            )
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .testTag("routeCanvas")
            .pointerInput(bitmap.width, bitmap.height, rotationQuarterTurns) {
                awaitEachGesture {
                    awaitFirstDown()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            if (centroid.x.isFinite() && centroid.y.isFinite()) {
                                updateViewportGesture(
                                    event.calculateZoom(),
                                    event.calculatePan(),
                                    centroid,
                                    Offset(size.width / 2f, size.height / 2f),
                                    zoom,
                                    pan,
                                ) { newZoom, newPan -> zoom = newZoom; pan = newPan }
                            }
                        }
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) break
                    }
                }
            },
    ) {
        val viewport = viewportTransform(
            size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
            rotationDegrees = animatedRotation,
        )
        withViewport(viewport) {
            drawFittedImage(image, viewport.base)
            val centers = route.map { viewport.base.toCanvas(it.center) }
            drawRouteLines(centers, progress.value, routeColor)
        }
        val visitedIds = route.mapTo(mutableSetOf()) { it.id }
        drawReferencePoints(
            points = allPoints,
            viewport = viewport,
            zoom = zoom,
            useTypeColors = true,
            mutedPointIds = allPoints.mapNotNullTo(mutableSetOf()) { point ->
                point.id.takeUnless(visitedIds::contains)
            },
        )
    }
}

/** Zoomable map on which two taps define a real-world distance calibration line. */
@Composable
fun DistanceCalibrationCanvas(
    bitmap: Bitmap,
    start: Point2D?,
    end: Point2D?,
    onLineChange: (Point2D?, Point2D?) -> Unit,
    controlPoints: List<ControlPoint> = emptyList(),
    rotationQuarterTurns: Int = 0,
    recenterKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val latestStart by rememberUpdatedState(start)
    val latestEnd by rememberUpdatedState(end)
    val latestOnLineChange by rememberUpdatedState(onLineChange)
    val animatedRotation = animatedRotationDegrees(rotationQuarterTurns)
    val animationsEnabled = LocalAnimationsEnabled.current
    LaunchedEffect(rotationQuarterTurns) {
        zoom = 1f
        pan = Offset.Zero
    }
    LaunchedEffect(recenterKey) {
        if (recenterKey > 0) {
            animateViewportToCenter(zoom, pan, animationsEnabled) { newZoom, newPan ->
                zoom = newZoom
                pan = newPan
            }
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { newSize ->
                pan = stablePanAfterResize(
                    oldSize = viewportSize,
                    newSize = newSize,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    rotationQuarterTurns = rotationQuarterTurns,
                    zoom = zoom,
                    currentPan = pan,
                )
                viewportSize = newSize
            }
            .testTag("distanceCalibrationCanvas")
            .pointerInput(bitmap.width, bitmap.height, rotationQuarterTurns) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val initialViewport = viewportTransform(
                        size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
                    )
                    val handles = listOfNotNull(latestStart, latestEnd)
                    var selectedHandle = handles
                        .map(initialViewport::toCanvas)
                        .mapIndexed { index, handle -> index to (handle - down.position).getDistance() }
                        .filter { (_, distance) -> distance <= CORNER_TOUCH_RADIUS_PX }
                        .minByOrNull { (_, distance) -> distance }
                        ?.first
                    var moved = 0f
                    var usedMultiTouch = false
                    var lastPosition = down.position

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            usedMultiTouch = true
                            selectedHandle = null
                            updateViewportGesture(
                                event.calculateZoom(),
                                event.calculatePan(),
                                event.calculateCentroid(useCurrent = true),
                                Offset(size.width / 2f, size.height / 2f),
                                zoom,
                                pan,
                            ) { newZoom, newPan -> zoom = newZoom; pan = newPan }
                            event.changes.forEach { it.consume() }
                        } else {
                            event.changes.firstOrNull()?.let { change ->
                                lastPosition = change.position
                                if (change.pressed) {
                                    val delta = change.position - change.previousPosition
                                    moved += delta.getDistance()
                                    if (selectedHandle != null) {
                                        val viewport = viewportTransform(
                                            size, bitmap.width, bitmap.height,
                                            rotationQuarterTurns, zoom, pan,
                                        )
                                        val point = viewport.toImage(change.position)
                                        if (selectedHandle == 0) {
                                            latestOnLineChange(point, latestEnd)
                                        } else {
                                            latestOnLineChange(latestStart, point)
                                        }
                                    } else if (zoom > 1f && (latestEnd != null || moved > TAP_SLOP_PX)) {
                                        pan += delta
                                    }
                                    change.consume()
                                }
                            }
                        }
                        if (event.changes.all { !it.pressed }) break
                    }

                    if (!usedMultiTouch && moved <= TAP_SLOP_PX && selectedHandle == null) {
                        val viewport = viewportTransform(
                            size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
                        )
                        val point = viewport.toImage(lastPosition)
                        when {
                            latestStart == null -> latestOnLineChange(point, null)
                            latestEnd == null -> latestOnLineChange(latestStart, point)
                        }
                    }
                }
            },
    ) {
        val viewport = viewportTransform(
            size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
            rotationDegrees = animatedRotation,
        )
        withViewport(viewport) {
            drawFittedImage(image, viewport.base)
            val startCanvas = start?.let(viewport.base::toCanvas)
            val endCanvas = end?.let(viewport.base::toCanvas)
            if (startCanvas != null && endCanvas != null) {
                drawLine(
                    color = Color(0xFFFFC107),
                    start = startCanvas,
                    end = endCanvas,
                    strokeWidth = 6f / zoom,
                    cap = StrokeCap.Round,
                )
            }
            listOfNotNull(startCanvas, endCanvas).forEach { point ->
                val pointScale = sqrt(zoom)
                drawCircle(Color.White, radius = 14f / pointScale, center = point)
                drawCircle(Color(0xFFE91E63), radius = 10f / pointScale, center = point)
            }
        }
        drawReferencePoints(controlPoints, viewport, zoom, useTypeColors = false)
    }
}

/** Zoomable control editor: drag a marker to move it and tap it to edit its metadata. */
@Composable
fun InteractiveControlPointCanvas(
    bitmap: Bitmap,
    points: List<ControlPoint>,
    rotationQuarterTurns: Int,
    onPointMoved: (ControlPoint) -> Unit,
    onPointSelected: (ControlPoint) -> Unit,
    onViewportCenterChange: (Point2D) -> Unit,
    recenterKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val latestPoints by rememberUpdatedState(points)
    val latestOnPointMoved by rememberUpdatedState(onPointMoved)
    val latestOnPointSelected by rememberUpdatedState(onPointSelected)
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val latestOnViewportCenterChange by rememberUpdatedState(onViewportCenterChange)
    val animatedRotation = animatedRotationDegrees(rotationQuarterTurns)
    val animationsEnabled = LocalAnimationsEnabled.current
    LaunchedEffect(rotationQuarterTurns) {
        zoom = 1f
        pan = Offset.Zero
    }
    LaunchedEffect(recenterKey) {
        if (recenterKey > 0) {
            animateViewportToCenter(zoom, pan, animationsEnabled) { newZoom, newPan ->
                zoom = newZoom
                pan = newPan
            }
        }
    }
    LaunchedEffect(viewportSize, rotationQuarterTurns, zoom, pan) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val viewport = viewportTransform(
                viewportSize, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
            )
            latestOnViewportCenterChange(
                viewport.toImage(Offset(viewportSize.width / 2f, viewportSize.height / 2f)),
            )
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { newSize ->
                pan = stablePanAfterResize(
                    oldSize = viewportSize,
                    newSize = newSize,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    rotationQuarterTurns = rotationQuarterTurns,
                    zoom = zoom,
                    currentPan = pan,
                )
                viewportSize = newSize
            }
            .testTag("controlPointCanvas")
            .pointerInput(bitmap.width, bitmap.height, rotationQuarterTurns) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val initialViewport = viewportTransform(
                        size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
                    )
                    var selectedId = latestPoints
                        .map { it.id to (initialViewport.toCanvas(it.center) - down.position).getDistance() }
                        .filter { (_, distance) -> distance <= CORNER_TOUCH_RADIUS_PX }
                        .minByOrNull { (_, distance) -> distance }
                        ?.first
                    var moved = 0f
                    var usedMultiTouch = false

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            usedMultiTouch = true
                            selectedId = null
                            updateViewportGesture(
                                event.calculateZoom(),
                                event.calculatePan(),
                                event.calculateCentroid(useCurrent = true),
                                Offset(size.width / 2f, size.height / 2f),
                                zoom,
                                pan,
                            ) { newZoom, newPan -> zoom = newZoom; pan = newPan }
                            event.changes.forEach { it.consume() }
                        } else {
                            event.changes.firstOrNull()?.let { change ->
                                if (change.pressed) {
                                    val delta = change.position - change.previousPosition
                                    moved += delta.getDistance()
                                    val selected = latestPoints.firstOrNull { it.id == selectedId }
                                    if (selected != null) {
                                        val viewport = viewportTransform(
                                            size, bitmap.width, bitmap.height,
                                            rotationQuarterTurns, zoom, pan,
                                        )
                                        latestOnPointMoved(selected.copy(center = viewport.toImage(change.position)))
                                    } else if (zoom > 1f) {
                                        pan += delta
                                    }
                                    change.consume()
                                }
                            }
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                    if (!usedMultiTouch && moved <= TAP_SLOP_PX) {
                        latestPoints.firstOrNull { it.id == selectedId }?.let(latestOnPointSelected)
                    }
                }
            },
    ) {
        val viewport = viewportTransform(
            size, bitmap.width, bitmap.height, rotationQuarterTurns, zoom, pan,
            rotationDegrees = animatedRotation,
        )
        withViewport(viewport) {
            drawFittedImage(image, viewport.base)
        }
        drawReferencePoints(points, viewport, zoom, useTypeColors = true)
        drawLine(
            color = Color.White,
            start = center - Offset(36f, 0f),
            end = center + Offset(36f, 0f),
            strokeWidth = 8f,
        )
        drawLine(
            color = Color.White,
            start = center - Offset(0f, 36f),
            end = center + Offset(0f, 36f),
            strokeWidth = 8f,
        )
        drawLine(
            color = Color(0xFF0061A4),
            start = center - Offset(36f, 0f),
            end = center + Offset(36f, 0f),
            strokeWidth = 3f,
        )
        drawLine(
            color = Color(0xFF0061A4),
            start = center - Offset(0f, 36f),
            end = center + Offset(0f, 36f),
            strokeWidth = 3f,
        )
    }
}

private fun DrawScope.drawRouteLines(
    points: List<Offset>,
    progress: Float,
    color: Color,
) {
    if (points.size < 2 || progress <= 0f) return
    val lengths = points.zipWithNext { first, second ->
        hypot(second.x - first.x, second.y - first.y)
    }
    var remaining = lengths.sum() * progress.coerceIn(0f, 1f)
    points.zipWithNext().forEachIndexed { index, (start, end) ->
        if (remaining <= 0f) return
        val length = lengths[index]
        val fraction = if (length == 0f) 1f else (remaining / length).coerceAtMost(1f)
        drawLine(
            color = color,
            start = start,
            end = start + (end - start) * fraction,
            strokeWidth = 7f,
            cap = StrokeCap.Round,
        )
        remaining -= length
    }
}

private fun DrawScope.drawReferencePoints(
    points: List<ControlPoint>,
    viewport: ViewportTransform,
    zoom: Float,
    useTypeColors: Boolean,
    mutedPointIds: Set<String> = emptySet(),
) {
    val pointScale = sqrt(zoom).coerceAtMost(2.4f)
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(0, 73, 87)
        textSize = 18f * pointScale
        isFakeBoldText = true
    }
    val outlinePaint = Paint(labelPaint).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    points.forEach { point ->
        val isMuted = point.id in mutedPointIds
        labelPaint.alpha = if (isMuted) 125 else 255
        outlinePaint.alpha = if (isMuted) 150 else 255
        val center = viewport.toCanvas(point.center)
        val markerColor = if (isMuted) {
            Color(0xFF7A7A7A)
        } else if (useTypeColors) {
            when (point.type) {
                ControlPointType.START -> Color(0xFF2E7D32)
                ControlPointType.FINISH -> Color(0xFFC62828)
                ControlPointType.START_FINISH -> Color(0xFF6A1B9A)
                ControlPointType.CONTROL -> Color(0xFFE91E63)
            }
        } else {
            Color(0xFF007C91)
        }
        drawCircle(
            color = Color.White.copy(alpha = if (isMuted) 0.65f else 1f),
            radius = 13f * pointScale,
            center = center,
        )
        drawCircle(markerColor, radius = 9f * pointScale, center = center)
        val label = when (point.type) {
            ControlPointType.START -> "S"
            ControlPointType.FINISH -> "F"
            ControlPointType.START_FINISH -> "S/F"
            ControlPointType.CONTROL -> point.code.toString()
        }
        val labelX = center.x + 13f * pointScale
        val labelY = center.y - 10f * pointScale
        drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, outlinePaint)
        drawContext.canvas.nativeCanvas.drawText(
            label,
            labelX,
            labelY,
            labelPaint,
        )
    }
}

/** Keeps the same map coordinate under the viewport center when the IME resizes an editor canvas. */
internal fun stablePanAfterResize(
    oldSize: IntSize,
    newSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    rotationQuarterTurns: Int,
    zoom: Float,
    currentPan: Offset,
): Offset {
    if (zoom <= 1f || oldSize.width <= 0 || oldSize.height <= 0 ||
        newSize.width <= 0 || newSize.height <= 0 || oldSize == newSize
    ) return currentPan
    val oldViewport = viewportTransform(
        oldSize, imageWidth, imageHeight, rotationQuarterTurns, zoom, currentPan,
    )
    val focus = oldViewport.toImage(Offset(oldSize.width / 2f, oldSize.height / 2f))
    val newViewport = viewportTransform(
        newSize, imageWidth, imageHeight, rotationQuarterTurns, zoom, Offset.Zero,
    )
    return Offset(newSize.width / 2f, newSize.height / 2f) - newViewport.toCanvas(focus)
}

private data class ImageTransform(
    val scale: Float,
    val offset: Offset,
    val destinationSize: IntSize,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    fun toCanvas(point: Point2D): Offset = Offset(
        x = offset.x + point.x * scale,
        y = offset.y + point.y * scale,
    )

    fun toImage(point: Offset): Point2D = Point2D(
        x = ((point.x - offset.x) / scale).coerceIn(0f, imageWidth.toFloat()),
        y = ((point.y - offset.y) / scale).coerceIn(0f, imageHeight.toFloat()),
    )
}

private data class ViewportTransform(
    val base: ImageTransform,
    val center: Offset,
    val rotationDegrees: Float,
    val zoom: Float,
    val pan: Offset,
) {
    fun toCanvas(point: Point2D): Offset {
        val rotated = rotateAroundCenter(base.toCanvas(point), center, rotationDegrees)
        return center + (rotated - center) * zoom + pan
    }

    fun toImage(point: Offset): Point2D {
        val unscaled = center + (point - pan - center) / zoom
        val unrotated = rotateAroundCenter(unscaled, center, -rotationDegrees)
        return base.toImage(unrotated)
    }
}

@Composable
private fun animatedRotationDegrees(rotationQuarterTurns: Int): Float {
    val animationsEnabled = LocalAnimationsEnabled.current
    return animateFloatAsState(
        targetValue = rotationQuarterTurns * 90f,
        animationSpec = tween(if (animationsEnabled) 180 else 0),
        label = "mapRotation",
    ).value
}

private suspend fun animateViewportToCenter(
    currentZoom: Float,
    currentPan: Offset,
    animationsEnabled: Boolean,
    onUpdate: (Float, Offset) -> Unit,
) {
    if (!animationsEnabled) {
        onUpdate(1f, Offset.Zero)
        return
    }
    animate(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = tween(durationMillis = 320),
    ) { progress, _ ->
        onUpdate(
            currentZoom + (1f - currentZoom) * progress,
            currentPan * (1f - progress),
        )
    }
}

private fun viewportTransform(
    canvasSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    rotationQuarterTurns: Int,
    zoom: Float,
    pan: Offset,
    rotationDegrees: Float = rotationQuarterTurns * 90f,
): ViewportTransform = viewportTransform(
    canvasSize = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
    imageWidth = imageWidth,
    imageHeight = imageHeight,
    rotationQuarterTurns = rotationQuarterTurns,
    zoom = zoom,
    pan = pan,
    rotationDegrees = rotationDegrees,
)

private fun viewportTransform(
    canvasSize: Size,
    imageWidth: Int,
    imageHeight: Int,
    rotationQuarterTurns: Int,
    zoom: Float,
    pan: Offset,
    rotationDegrees: Float = rotationQuarterTurns * 90f,
): ViewportTransform = ViewportTransform(
    base = fittedImageTransform(
        canvasSize = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        rotationQuarterTurns = rotationQuarterTurns,
    ),
    center = Offset(canvasSize.width / 2f, canvasSize.height / 2f),
    rotationDegrees = rotationDegrees,
    zoom = zoom,
    pan = pan,
)

private fun rotateAroundCenter(point: Offset, center: Offset, degrees: Float): Offset {
    val relative = point - center
    val radians = Math.toRadians(degrees.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    val rotated = Offset(
        relative.x * cosine - relative.y * sine,
        relative.x * sine + relative.y * cosine,
    )
    return center + rotated
}

private fun DrawScope.withViewport(
    viewport: ViewportTransform,
    drawBlock: DrawScope.() -> Unit,
) {
    withTransform(
        transformBlock = {
            translate(viewport.pan.x, viewport.pan.y)
            scale(viewport.zoom, viewport.zoom, viewport.center)
            rotate(viewport.rotationDegrees, viewport.center)
        },
        drawBlock = drawBlock,
    )
}

private fun updateViewportGesture(
    zoomChange: Float,
    panChange: Offset,
    centroid: Offset,
    viewportCenter: Offset,
    currentZoom: Float,
    currentPan: Offset,
    onUpdate: (Float, Offset) -> Unit,
) {
    val (updatedZoom, updatedPan) = updatedViewportForGesture(
        zoomChange,
        panChange,
        centroid,
        viewportCenter,
        currentZoom,
        currentPan,
    )
    onUpdate(updatedZoom, updatedPan)
}

internal fun updatedViewportForGesture(
    zoomChange: Float,
    panChange: Offset,
    centroid: Offset,
    viewportCenter: Offset,
    currentZoom: Float,
    currentPan: Offset,
): Pair<Float, Offset> {
    if (
        !zoomChange.isFinite() || zoomChange <= 0f ||
        !panChange.x.isFinite() || !panChange.y.isFinite() ||
        !centroid.x.isFinite() || !centroid.y.isFinite()
    ) return currentZoom to currentPan
    val updatedZoom = (currentZoom * zoomChange).coerceIn(1f, 12f)
    val zoomRatio = updatedZoom / currentZoom
    val previousCentroid = centroid - panChange
    val focalPan = centroid - viewportCenter -
        (previousCentroid - viewportCenter - currentPan) * zoomRatio
    val updatedPan = if (updatedZoom == 1f) Offset.Zero else focalPan
    return updatedZoom to updatedPan
}

private fun fittedImageTransform(
    canvasSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): ImageTransform = fittedImageTransform(
    canvasSize = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
    imageWidth = imageWidth,
    imageHeight = imageHeight,
)

private fun fittedImageTransform(
    canvasSize: Size,
    imageWidth: Int,
    imageHeight: Int,
    rotationQuarterTurns: Int = 0,
): ImageTransform {
    val swapsDimensions = rotationQuarterTurns.mod(2) != 0
    val rotatedWidth = if (swapsDimensions) imageHeight else imageWidth
    val rotatedHeight = if (swapsDimensions) imageWidth else imageHeight
    val scale = min(canvasSize.width / rotatedWidth, canvasSize.height / rotatedHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return ImageTransform(
        scale = scale,
        offset = Offset((canvasSize.width - width) / 2f, (canvasSize.height - height) / 2f),
        destinationSize = IntSize(width.toInt(), height.toInt()),
        imageWidth = imageWidth,
        imageHeight = imageHeight,
    )
}

private fun DrawScope.drawFittedImage(image: ImageBitmap, transform: ImageTransform) {
    drawImage(
        image = image,
        dstOffset = IntOffset(transform.offset.x.toInt(), transform.offset.y.toInt()),
        dstSize = transform.destinationSize,
    )
}

private fun MapBoundary.withCorner(index: Int, point: Point2D): MapBoundary = when (index) {
    0 -> copy(topLeft = point)
    1 -> copy(topRight = point)
    2 -> copy(bottomRight = point)
    3 -> copy(bottomLeft = point)
    else -> this
}

internal fun updateBoundaryCorner(
    boundary: MapBoundary,
    cornerIndex: Int,
    dragPosition: Offset,
    canvasSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
): MapBoundary {
    val imagePoint = fittedImageTransform(canvasSize, imageWidth, imageHeight).toImage(dragPosition)
    return boundary.withCorner(cornerIndex, imagePoint)
}

private const val CORNER_TOUCH_RADIUS_PX = 56f
private const val TAP_SLOP_PX = 12f
