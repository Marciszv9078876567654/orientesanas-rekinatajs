package com.orientesanasrekinatajs.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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

/** Modal route breakdown with one row for every point in visit order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailsBottomSheet(
    route: OptimizedRoute,
    alternativeRoutes: List<OptimizedRoute> = emptyList(),
    nextLongestRouteCount: Int = 0,
    selectedRouteIndex: Int = 0,
    onRouteSelected: (Int) -> Unit = {},
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val higherScoreRoutes = alternativeRoutes.take(nextLongestRouteCount)
    val lowerScoreRoutes = alternativeRoutes.drop(nextLongestRouteCount)
    val routes = higherScoreRoutes + route + lowerScoreRoutes
    val primaryRouteIndex = higherScoreRoutes.size
    val safeSelectedIndex = selectedRouteIndex.coerceIn(routes.indices)
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
                val scoreDifference = candidate.totalScore - route.totalScore
                val distanceDifference = candidate.totalDistanceMeters - route.totalDistanceMeters
                val isSelected = safeSelectedIndex == index
                Tab(
                    selected = isSelected,
                    onClick = { onRouteSelected(index) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    when {
                                        index == primaryRouteIndex -> stringResource(
                                            R.string.primary_route_tab,
                                        )
                                        scoreDifference > 0 -> pluralStringResource(
                                            R.plurals.more_score_points_route_tab,
                                            scoreDifference,
                                            scoreDifference,
                                        )
                                        else -> pluralStringResource(
                                            R.plurals.fewer_score_points_route_tab,
                                            -scoreDifference,
                                            -scoreDifference,
                                        )
                                    },
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.selected_route),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            if (index != primaryRouteIndex) {
                                Text(
                                    text = stringResource(
                                        if (distanceDifference >= 0f) {
                                            R.string.route_distance_difference_longer
                                        } else {
                                            R.string.route_distance_difference_shorter
                                        },
                                        kotlin.math.abs(distanceDifference),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                )
            }
        }
        Text(
            text = stringResource(
                R.string.route_summary,
                selectedRoute.totalDistanceMeters,
                selectedRoute.totalScore,
            ),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        )
        RouteStepTable(
            route = selectedRoute,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

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
