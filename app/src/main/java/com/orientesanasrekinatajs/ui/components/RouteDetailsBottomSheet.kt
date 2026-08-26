package com.orientesanasrekinatajs.ui.components

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                            routeDetailColor(routeMetadata[candidate.id]?.colorIndex ?: 0)
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

private fun routeDetailColor(index: Int): androidx.compose.ui.graphics.Color = listOf(
    0xFF455A64, 0xFF3F51B5, 0xFF009688, 0xFFFF9800, 0xFF9C27B0,
    0xFF03A9F4, 0xFF8BC34A, 0xFFFF5722, 0xFF795548, 0xFF607D8B,
).let { androidx.compose.ui.graphics.Color(it[index.mod(it.size)]) }

/** Lightweight route-order editor shell. Drag-and-drop behavior will be added separately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorBottomSheet(
    route: OptimizedRoute,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.edit_route),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(
            text = stringResource(R.string.reorder_route_points_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            itemsIndexed(
                items = route.path,
                key = { index, control -> "$index:${control.id}" },
            ) { index, control ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = when (control.type) {
                            ControlPointType.START -> "S"
                            ControlPointType.FINISH -> "F"
                            ControlPointType.START_FINISH -> "-"
                            ControlPointType.CONTROL -> control.code.toString()
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {}, enabled = false) {
                        Icon(Icons.Default.DragHandle, contentDescription = null)
                    }
                }
                if (index < route.path.lastIndex) HorizontalDivider()
            }
        }
    }
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
