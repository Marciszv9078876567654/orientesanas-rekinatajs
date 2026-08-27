package com.orientesanasrekinatajs.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.RouteMetadata

/** Modal route breakdown with one row for every point in visit order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailsBottomSheet(
    route: OptimizedRoute,
    alternativeRoutes: List<OptimizedRoute> = emptyList(),
    nextLongestRouteCount: Int = 0,
    routeMetadata: Map<String, RouteMetadata> = emptyMap(),
    selectedRouteId: String? = null,
    showPointsPerKilometer: Boolean = false,
    onRouteSelected: (String) -> Unit = {},
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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        PrimaryScrollableTabRow(
            selectedTabIndex = safeSelectedIndex,
            edgePadding = 12.dp,
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
                    onClick = { onRouteSelected(candidate.id) },
                    modifier = Modifier
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
                            val (distanceStatistics, scoreStatistics) = if (isSelected) {
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
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Edits the selected route's visit order and set of scored controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorBottomSheet(
    route: OptimizedRoute,
    allPoints: List<ControlPoint>,
    onSave: (List<ControlPoint>) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editedPath by remember(route.id) { mutableStateOf(route.path) }
    var addPointMenuExpanded by remember { mutableStateOf(false) }
    val usedPointIds = editedPath.mapTo(mutableSetOf(), ControlPoint::id)
    val availableControls = allPoints.filter { point ->
        point.type == ControlPointType.CONTROL && point.id !in usedPointIds
    }.sortedBy(ControlPoint::code)
    val canReverse = editedPath.any { it.type == ControlPointType.START_FINISH }
    val movePoint: (Int, Int) -> Unit = { from, to ->
        val reordered = editedPath.toMutableList()
        val moved = reordered.removeAt(from)
        reordered.add(to, moved)
        editedPath = reordered
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                                    editedPath = editedPath.toMutableList().apply {
                                        add(insertionIndex, point)
                                    }
                                    addPointMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { editedPath = editedPath.reversed() },
                    enabled = canReverse,
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null)
                    Text(stringResource(R.string.reverse_route))
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 6.dp),
        ) {
            itemsIndexed(
                items = editedPath,
                key = { index, control -> "$index:${control.id}" },
            ) { index, control ->
                val isControl = control.type == ControlPointType.CONTROL
                val canMoveUp = isControl && index > 0 &&
                    editedPath[index - 1].type == ControlPointType.CONTROL
                val canMoveDown = isControl && index < editedPath.lastIndex &&
                    editedPath[index + 1].type == ControlPointType.CONTROL
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = routeEditorPointLabel(control),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { movePoint(index, index - 1) }, enabled = canMoveUp) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = stringResource(R.string.move_point_up),
                        )
                    }
                    IconButton(onClick = { movePoint(index, index + 1) }, enabled = canMoveDown) {
                        Icon(
                            Icons.Default.ArrowDownward,
                            contentDescription = stringResource(R.string.move_point_down),
                        )
                    }
                    IconButton(
                        onClick = { editedPath = editedPath.filterIndexed { i, _ -> i != index } },
                        enabled = isControl,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.remove_route_point),
                        )
                    }
                }
                if (index < editedPath.lastIndex) HorizontalDivider()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.cancel)) }
            Button(
                onClick = { onSave(editedPath) },
                enabled = editedPath != route.path && editedPath.size >= 2,
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

private fun routeEditorPointLabel(point: ControlPoint): String = when (point.type) {
    ControlPointType.START -> "S"
    ControlPointType.FINISH -> "F"
    ControlPointType.START_FINISH -> "S/F"
    ControlPointType.CONTROL -> "${point.code} (${point.points})"
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
