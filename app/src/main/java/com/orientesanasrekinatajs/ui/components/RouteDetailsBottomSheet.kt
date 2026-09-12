package com.orientesanasrekinatajs.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled

enum class RouteDetailsPanelState { MINIMIZED, HALF, EXPANDED, DISMISSED }
enum class RouteEditorPanelState { MINIMIZED, HALF, EXPANDED }

/** Draggable route breakdown with one row for every point in visit order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailsBottomSheet(
    route: OptimizedRoute,
    alternativeRoutes: List<OptimizedRoute> = emptyList(),
    nextLongestRouteCount: Int = 0,
    routeMetadata: Map<String, RouteMetadata> = emptyMap(),
    selectedRouteId: String? = null,
    showPointsPerKilometer: Boolean = false,
    onShowPointsPerKilometerChange: (Boolean) -> Unit = {},
    showRelativeValues: Boolean = false,
    onShowRelativeValuesChange: (Boolean) -> Unit = {},
    showPointNumbering: Boolean = false,
    onShowPointNumberingChange: (Boolean) -> Unit = {},
    onRouteSelected: (String) -> Unit = {},
    panelState: RouteDetailsPanelState,
    onPanelStateChange: (RouteDetailsPanelState) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val higherScoreRoutes = alternativeRoutes.take(nextLongestRouteCount)
    val lowerScoreRoutes = alternativeRoutes.drop(nextLongestRouteCount)
    val allRoutes = higherScoreRoutes + route + lowerScoreRoutes
    val routes = allRoutes.filterNot { routeMetadata[it.id]?.isHidden == true }
    val primaryRouteIndex = routes.indexOfFirst { it.id == route.id }
    val safeSelectedIndex = routes.indexOfFirst { it.id == selectedRouteId }
        .takeIf { it >= 0 } ?: primaryRouteIndex.takeIf { it >= 0 } ?: 0
    val selectedRoute = routes[safeSelectedIndex]
    val toggleColors = IconButtonDefaults.iconToggleButtonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val minimizedHeight = if (showPointsPerKilometer) 166.dp else 142.dp
        val routeTabMinimumHeight = if (showPointsPerKilometer) 104.dp else 48.dp
        val minimumHeightPx = with(density) { minimizedHeight.toPx() }
        val maximumHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(minimumHeightPx)
        val halfHeightPx = (constraints.maxHeight * 0.5f).coerceIn(
            minimumHeightPx,
            maximumHeightPx,
        )
        val targetHeightPx = when (panelState) {
            RouteDetailsPanelState.MINIMIZED -> minimumHeightPx
            RouteDetailsPanelState.HALF -> halfHeightPx
            RouteDetailsPanelState.EXPANDED -> maximumHeightPx
            RouteDetailsPanelState.DISMISSED -> 0f
        }
        var panelHeightPx by remember(constraints.maxHeight) { mutableFloatStateOf(0f) }
        var dragDistanceY by remember { mutableFloatStateOf(0f) }
        var isDetailsDragging by remember { mutableStateOf(false) }
        var settleRequest by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        val animationsEnabled = LocalAnimationsEnabled.current
        LaunchedEffect(
            panelState,
            settleRequest,
            constraints.maxHeight,
            animationsEnabled,
            showPointsPerKilometer,
            isDetailsDragging,
        ) {
            if (isDetailsDragging) return@LaunchedEffect
            if (!animationsEnabled) {
                panelHeightPx = targetHeightPx
            } else {
                animate(
                    initialValue = panelHeightPx,
                    targetValue = targetHeightPx,
                    animationSpec = tween(240),
                ) { value, _ -> panelHeightPx = value }
            }
            if (panelState == RouteDetailsPanelState.DISMISSED) onDismissRequest()
        }
        val panelDragState = rememberDraggableState { delta ->
            dragDistanceY += delta
            panelHeightPx = (panelHeightPx - delta).coerceIn(0f, maximumHeightPx)
        }
        val fadeDistancePx = with(density) { 36.dp.toPx() }
        val detailsAlpha = ((panelHeightPx - minimumHeightPx) / fadeDistancePx).coerceIn(0f, 1f)
        val dismissHeightPx = minimumHeightPx * 0.35f
        val fullScreenProgress = if (maximumHeightPx > halfHeightPx) {
            ((panelHeightPx - halfHeightPx) / (maximumHeightPx - halfHeightPx)).coerceIn(0f, 1f)
        } else {
            1f
        }
        val topCorner = 28.dp * (1f - fullScreenProgress)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { panelHeightPx.toDp() }),
            shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = Color.White,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp * (1f - fullScreenProgress),
        ) {
        Column(
            Modifier
                .fillMaxHeight()
                .clipToBounds()
                .minimumContentHeightPx { minimumHeightPx },
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = panelDragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { isDetailsDragging = true; dragDistanceY = 0f },
                    onDragStopped = { velocity ->
                        if (dragDistanceY > 0f && panelHeightPx <= dismissHeightPx) {
                            onPanelStateChange(RouteDetailsPanelState.DISMISSED)
                        } else {
                            val states = listOf(RouteDetailsPanelState.MINIMIZED, RouteDetailsPanelState.HALF, RouteDetailsPanelState.EXPANDED)
                            val index = settledPanelIndex(
                                listOf(minimumHeightPx, halfHeightPx, maximumHeightPx),
                                states.indexOf(panelState).coerceAtLeast(0), panelHeightPx,
                                dragDistanceY, velocity, density.density,
                            )
                            onPanelStateChange(states[index])
                        }
                        isDetailsDragging = false
                        settleRequest++
                    },
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(48.dp)) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconToggleButton(
                        checked = showPointsPerKilometer,
                        modifier = Modifier.size(36.dp),
                        colors = toggleColors,
                        onCheckedChange = onShowPointsPerKilometerChange,
                    ) {
                        Icon(Icons.Default.Speed, stringResource(R.string.points_per_kilometer), Modifier.size(20.dp))
                    }
                    IconToggleButton(
                        checked = showRelativeValues,
                        modifier = Modifier.size(36.dp),
                        colors = toggleColors,
                        onCheckedChange = onShowRelativeValuesChange,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, stringResource(R.string.use_relative_values), Modifier.size(20.dp))
                    }
                }
                Text(
                    stringResource(R.string.route_details),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 104.dp)
                        .align(Alignment.Center),
                )
                IconToggleButton(
                    checked = showPointNumbering,
                    colors = toggleColors,
                    onCheckedChange = onShowPointNumberingChange,
                    modifier = Modifier.align(Alignment.CenterEnd).size(36.dp),
                ) {
                    Icon(Icons.Default.FormatListNumbered, stringResource(R.string.show_point_numbering), Modifier.size(20.dp))
                }
            }
        }
        PrimaryScrollableTabRow(
            selectedTabIndex = safeSelectedIndex,
            edgePadding = 12.dp,
            contentColor = Color.White,
        ) {
            routes.forEachIndexed { index, candidate ->
                val primaryScoreDifference = candidate.totalScore - route.totalScore
                val selectedScoreDifference = candidate.totalScore - selectedRoute.totalScore
                val selectedDistanceDifference =
                    candidate.totalDistanceMeters - selectedRoute.totalDistanceMeters
                val isSelected = safeSelectedIndex == index
                val customName = routeMetadata[candidate.id]
                    ?.name
                    ?.takeIf(String::isNotBlank)
                Tab(
                    selected = isSelected,
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White,
                    onClick = { onRouteSelected(candidate.id) },
                    modifier = Modifier
                        .heightIn(min = routeTabMinimumHeight)
                        .padding(horizontal = 3.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            routeColor(routeMetadata[candidate.id]?.colorIndex ?: 0)
                                .copy(alpha = if (isSelected) 0.34f else 0.16f),
                        ),
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    customName ?: when {
                                        index == primaryRouteIndex -> stringResource(
                                            R.string.primary_route_tab,
                                        )
                                        primaryScoreDifference > 0 -> pluralStringResource(
                                            R.plurals.more_score_points_route_tab,
                                            primaryScoreDifference,
                                            primaryScoreDifference,
                                        )
                                        primaryScoreDifference < 0 -> pluralStringResource(
                                            R.plurals.fewer_score_points_route_tab,
                                            -primaryScoreDifference,
                                            -primaryScoreDifference,
                                        )
                                        else -> stringResource(R.string.zero_score_points_route_tab)
                                    },
                                )
                            }
                            val (distanceStatistics, scoreStatistics) = if (isSelected || !showRelativeValues) {
                                stringResource(
                                    R.string.distance_meters_format,
                                    candidate.totalDistanceMeters,
                                ) to pluralStringResource(
                                    R.plurals.route_score_points,
                                    candidate.totalScore,
                                    candidate.totalScore,
                                )
                            } else {
                                val distance = stringResource(
                                    if (selectedDistanceDifference >= 0f) {
                                        R.string.route_distance_difference_longer
                                    } else {
                                        R.string.route_distance_difference_shorter
                                    },
                                    kotlin.math.abs(selectedDistanceDifference),
                                )
                                val score = when {
                                    selectedScoreDifference > 0 -> pluralStringResource(
                                        R.plurals.more_score_points_route_tab,
                                        selectedScoreDifference,
                                        selectedScoreDifference,
                                    )
                                    selectedScoreDifference < 0 -> pluralStringResource(
                                        R.plurals.fewer_score_points_route_tab,
                                        -selectedScoreDifference,
                                        -selectedScoreDifference,
                                    )
                                    else -> pluralStringResource(
                                        R.plurals.more_score_points_route_tab,
                                        0,
                                        0,
                                    )
                                }
                                distance to score
                            }
                            Text(
                                text = distanceStatistics,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = scoreStatistics,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (showPointsPerKilometer) {
                                val pointsPerKilometer = if (candidate.totalDistanceMeters > 0f) {
                                    candidate.totalScore * 1_000f / candidate.totalDistanceMeters
                                } else {
                                    0f
                                }
                                Text(
                                    text = stringResource(
                                        R.string.route_points_per_kilometer_format,
                                        pointsPerKilometer,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                )
            }
        }
        RouteStepTable(
            route = selectedRoute,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { alpha = detailsAlpha },
        )
        }
        }
    }
}

/** Persistent route editor that leaves the map interactive while partially collapsed. */
@Composable
fun RouteEditorBottomSheet(
    route: OptimizedRoute,
    editedPath: List<ControlPoint>,
    allPoints: List<ControlPoint>,
    panelState: RouteEditorPanelState,
    onPanelStateChange: (RouteEditorPanelState) -> Unit,
    onPathChange: (List<ControlPoint>) -> Unit,
    onSaveRequest: () -> Unit,
    onCancelRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var addPointMenuExpanded by remember { mutableStateOf(false) }
    var isPointDragging by remember { mutableStateOf(false) }
    var settleRequest by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val animationsEnabled = LocalAnimationsEnabled.current
    val usedPointIds = editedPath.mapTo(mutableSetOf(), ControlPoint::id)
    val availableControls = allPoints.filter { point ->
        point.type == ControlPointType.CONTROL && point.id !in usedPointIds
    }.sortedBy(ControlPoint::code)
    val canReverse = editedPath.any { it.type == ControlPointType.START_FINISH }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val minimumHeightPx = with(density) { 176.dp.toPx() }
        val maximumHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(minimumHeightPx)
        val halfHeightPx = (constraints.maxHeight * 0.5f).coerceIn(
            minimumHeightPx,
            maximumHeightPx,
        )
        val targetHeightPx = when (panelState) {
            RouteEditorPanelState.MINIMIZED -> minimumHeightPx
            RouteEditorPanelState.HALF -> halfHeightPx
            RouteEditorPanelState.EXPANDED -> maximumHeightPx
        }
        var panelHeightPx by remember(constraints.maxHeight) { mutableFloatStateOf(0f) }
        var dragDistanceY by remember { mutableFloatStateOf(0f) }
        var isPanelDragging by remember { mutableStateOf(false) }
        LaunchedEffect(
            panelState,
            settleRequest,
            constraints.maxHeight,
            animationsEnabled,
            isPanelDragging,
        ) {
            if (isPanelDragging) return@LaunchedEffect
            if (!animationsEnabled) {
                panelHeightPx = targetHeightPx
            } else {
                animate(
                    initialValue = panelHeightPx,
                    targetValue = targetHeightPx,
                    animationSpec = tween(180),
                ) { value, _ -> panelHeightPx = value }
            }
        }
        val panelDragState = rememberDraggableState { delta ->
            dragDistanceY += delta
            panelHeightPx = (panelHeightPx - delta).coerceIn(minimumHeightPx, maximumHeightPx)
        }
        val fullScreenProgress = if (maximumHeightPx > halfHeightPx) {
            ((panelHeightPx - halfHeightPx) / (maximumHeightPx - halfHeightPx)).coerceIn(0f, 1f)
        } else {
            1f
        }
        val topCorner = 28.dp * (1f - fullScreenProgress)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .dynamicHeightPx { panelHeightPx },
            shape = RoundedCornerShape(topStart = topCorner, topEnd = topCorner),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp * (1f - fullScreenProgress),
        ) {
        Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = panelDragState,
                    orientation = Orientation.Vertical,
                    enabled = !isPointDragging,
                    onDragStarted = {
                        isPanelDragging = true
                        dragDistanceY = 0f
                    },
                    onDragStopped = { velocity ->
                        val states = listOf(RouteEditorPanelState.MINIMIZED, RouteEditorPanelState.HALF, RouteEditorPanelState.EXPANDED)
                        val index = settledPanelIndex(
                            listOf(minimumHeightPx, halfHeightPx, maximumHeightPx),
                            states.indexOf(panelState), panelHeightPx, dragDistanceY, velocity, density.density,
                        )
                        onPanelStateChange(states[index])
                        settleRequest++
                        isPanelDragging = false
                    },
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
            Text(
                text = stringResource(R.string.edit_route),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { addPointMenuExpanded = true },
                        enabled = availableControls.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(stringResource(R.string.add_route_point))
                    }
                    DropdownMenu(
                        expanded = addPointMenuExpanded,
                        onDismissRequest = { addPointMenuExpanded = false },
                    ) {
                        availableControls.forEach { point ->
                            DropdownMenuItem(
                                text = { Text(routeEditorPointLabel(point)) },
                                onClick = {
                                    val insertionIndex = editedPath.lastIndex.takeIf { index ->
                                        editedPath.getOrNull(index)?.type in setOf(
                                            ControlPointType.FINISH,
                                            ControlPointType.START_FINISH,
                                        )
                                    } ?: editedPath.size
                                    onPathChange(
                                        editedPath.toMutableList().apply { add(insertionIndex, point) },
                                    )
                                    addPointMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { onPathChange(editedPath.reversed()) },
                    enabled = canReverse,
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null)
                    Text(stringResource(R.string.reverse_route))
                }
            }
        }
        val showEditorList = panelState != RouteEditorPanelState.MINIMIZED ||
            panelHeightPx > minimumHeightPx
        if (showEditorList) {
            RouteEditorPointList(
                path = editedPath,
                onPathChange = onPathChange,
                onDraggingChange = { isPointDragging = it },
                modifier = Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                    alpha = ((panelHeightPx - minimumHeightPx) / with(density) { 36.dp.toPx() })
                        .coerceIn(0f, 1f)
                },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancelRequest) { Text(stringResource(R.string.cancel)) }
            Button(
                onClick = onSaveRequest,
                enabled = editedPath != route.path && editedPath.size >= 2,
            ) { Text(stringResource(R.string.save)) }
        }
        }
        }
    }
}

private fun routeEditorPointLabel(point: ControlPoint): String = when (point.type) {
    ControlPointType.START -> "S"
    ControlPointType.FINISH -> "F"
    ControlPointType.START_FINISH -> "S/F"
    ControlPointType.CONTROL -> "${point.code} (${point.points})"
}

/** Reads rapidly changing height state during layout, avoiding route-list recomposition per pixel. */
private fun Modifier.dynamicHeightPx(heightPx: () -> Float): Modifier = layout { measurable, constraints ->
    val height = heightPx().roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(
        constraints.copy(minHeight = height, maxHeight = height),
    )
    layout(placeable.width, height) { placeable.placeRelative(0, 0) }
}

/** Keeps dismissing panel content at its natural height while its visible background shrinks. */
private fun Modifier.minimumContentHeightPx(minimumHeightPx: () -> Float): Modifier =
    layout { measurable, constraints ->
        val visibleHeight = constraints.maxHeight
        val contentHeight = maxOf(visibleHeight, minimumHeightPx().roundToInt())
        val placeable = measurable.measure(
            constraints.copy(minHeight = contentHeight, maxHeight = contentHeight),
        )
        layout(placeable.width, visibleHeight) { placeable.placeRelative(0, 0) }
    }

/** Scrollable tabular representation used by the route details bottom sheet. */
@Composable
fun RouteStepTable(
    route: OptimizedRoute,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("routeStepTable")) {
        RouteTableRow(
            sequence = stringResource(R.string.sequence_header),
            control = stringResource(R.string.control_header),
            distance = stringResource(R.string.distance_header),
            score = stringResource(R.string.score_header),
            scoreTotal = stringResource(R.string.score_total_header),
            isHeader = true,
        )
        HorizontalDivider()
        LazyColumn {
            itemsIndexed(
                items = route.path,
                key = { index, control -> "$index:${control.id}" },
            ) { index, control ->
                val segment = route.segments.getOrNull(index - 1)
                RouteTableRow(
                    sequence = (index + 1).toString(),
                    control = when (control.type) {
                        ControlPointType.START -> "S"
                        ControlPointType.FINISH -> "F"
                        ControlPointType.START_FINISH -> "-"
                        ControlPointType.CONTROL -> control.code.toString()
                    },
                    distance = stringResource(
                        R.string.distance_meters_format,
                        segment?.distanceMeters ?: 0f,
                    ),
                    score = if (control.type == ControlPointType.CONTROL) {
                        control.points.toString()
                    } else {
                        "-"
                    },
                    scoreTotal = (segment?.accumulatedPoints ?: 0).toString(),
                    isHeader = false,
                )
                if (index < route.path.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RouteTableRow(
    sequence: String,
    control: String,
    distance: String,
    score: String,
    scoreTotal: String,
    isHeader: Boolean,
) {
    val style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
    val weight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        RouteTableCell(sequence, 0.35f, style, weight)
        RouteTableCell(control, 1.15f, style, weight)
        RouteTableCell(distance, 0.9f, style, weight)
        RouteTableCell(score, 0.75f, style, weight)
        RouteTableCell(scoreTotal, 1.1f, style, weight)
    }
}

@Composable
private fun RowScope.RouteTableCell(
    text: String,
    columnWeight: Float,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight,
) {
    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(columnWeight),
    )
}
