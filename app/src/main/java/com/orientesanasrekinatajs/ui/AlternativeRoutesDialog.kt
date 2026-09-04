package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.ui.components.routeColor
import com.orientesanasrekinatajs.ui.routing.AlternativeRouteCriteria

@Composable
internal fun AlternativeRoutesDialog(
    routes: List<OptimizedRoute>,
    initialSourceRouteId: String,
    routeMetadata: Map<String, RouteMetadata>,
    onSourceSelected: (String) -> Unit,
    isGenerating: Boolean,
    onGenerate: (AlternativeRouteCriteria) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val selectableRoutes = routes.filterNot { routeMetadata[it.id]?.isHidden == true }
    var sourceRouteId by rememberSaveable(initialSourceRouteId, selectableRoutes.map(OptimizedRoute::id)) {
        mutableStateOf(
            initialSourceRouteId.takeIf { initial -> selectableRoutes.any { it.id == initial } }
                ?: selectableRoutes.first().id,
        )
    }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    val sourceRoute = selectableRoutes.firstOrNull { it.id == sourceRouteId } ?: selectableRoutes.first()
    val maximumSplitIndex = (sourceRoute.path.lastIndex - 1).coerceAtLeast(0)
    var splitAfterIndex by rememberSaveable(sourceRoute.id) { mutableIntStateOf(0) }
    var countText by rememberSaveable { mutableStateOf("3") }
    var minDistanceText by rememberSaveable { mutableStateOf("") }
    var maxDistanceText by rememberSaveable { mutableStateOf("") }
    var minScoreText by rememberSaveable { mutableStateOf("") }
    var maxScoreText by rememberSaveable { mutableStateOf("") }
    var useRelativeDistanceValues by rememberSaveable { mutableStateOf(false) }
    var useRelativeScoreValues by rememberSaveable { mutableStateOf(false) }
    val count = countText.toIntOrNull()
    val minDistance = minDistanceText.localizedFloatOrNull()
    val maxDistance = maxDistanceText.localizedFloatOrNull()
    val minScore = minScoreText.toIntOrNull()
    val maxScore = maxScoreText.toIntOrNull()
    val fieldsValid = count != null && count in 1..20 &&
        (minDistanceText.isBlank() || minDistance != null) &&
        (maxDistanceText.isBlank() || maxDistance != null) &&
        (minScoreText.isBlank() || minScore != null) &&
        (maxScoreText.isBlank() || maxScore != null)
    val resolvedMinDistance = minDistance?.let {
        if (useRelativeDistanceValues) sourceRoute.totalDistanceMeters - it else it
    }
    val resolvedMaxDistance = maxDistance?.let {
        if (useRelativeDistanceValues) sourceRoute.totalDistanceMeters + it else it
    }
    val resolvedMinScore = minScore?.let {
        if (useRelativeScoreValues) sourceRoute.totalScore - it else it
    }
    val resolvedMaxScore = maxScore?.let {
        if (useRelativeScoreValues) sourceRoute.totalScore + it else it
    }
    val boundsValid = (resolvedMinDistance == null || resolvedMaxDistance == null ||
        resolvedMinDistance <= resolvedMaxDistance) &&
        (resolvedMinScore == null || resolvedMaxScore == null || resolvedMinScore <= resolvedMaxScore)

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        modifier = Modifier.clearFocusOnPointerDown(focusManager),
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .clearFocusOnPointerDown(focusManager),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.alternative_routes),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { sourceMenuExpanded = true },
                        enabled = !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor =
                                routeColor(routeMetadata[sourceRoute.id]?.colorIndex ?: 0)
                                    .copy(alpha = 0.22f),
                        ),
                    ) {
                        Box(
                            Modifier
                                .width(6.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(routeColor(routeMetadata[sourceRoute.id]?.colorIndex ?: 0)),
                        )
                        Text(
                            routeMetadata[sourceRoute.id]?.name?.ifBlank { null }
                                ?: managedRouteDefaultName(sourceRoute, routes.firstOrNull(), sourceRoute == routes.firstOrNull()),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false },
                    ) {
                        selectableRoutes.forEach { candidate ->
                            DropdownMenuItem(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        routeColor(routeMetadata[candidate.id]?.colorIndex ?: 0)
                                            .copy(alpha = if (candidate.id == sourceRoute.id) 0.24f else 0.10f),
                                    ),
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .width(6.dp)
                                                .height(28.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(
                                                    routeColor(routeMetadata[candidate.id]?.colorIndex ?: 0),
                                                ),
                                        )
                                        Text(
                                            routeMetadata[candidate.id]?.name?.ifBlank { null }
                                                ?: managedRouteDefaultName(
                                                    candidate, routes.firstOrNull(), candidate == routes.firstOrNull(),
                                                ),
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                },
                                onClick = {
                                    sourceRouteId = candidate.id
                                    sourceMenuExpanded = false
                                    onSourceSelected(candidate.id)
                                },
                            )
                        }
                    }
                }
                Text(
                    stringResource(
                        R.string.alternative_routes_primary_summary,
                        sourceRoute.totalDistanceMeters,
                        sourceRoute.totalScore,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text(
                    stringResource(
                        R.string.generate_alternatives_after_point,
                        restrictionPointLabel(sourceRoute.path[splitAfterIndex]),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = splitAfterIndex.toFloat(),
                    onValueChange = { splitAfterIndex = it.toInt().coerceIn(0, maximumSplitIndex) },
                    valueRange = 0f..maximumSplitIndex.coerceAtLeast(1).toFloat(),
                    steps = (maximumSplitIndex - 1).coerceAtLeast(0),
                    enabled = !isGenerating && maximumSplitIndex > 0,
                )
                Text(
                    stringResource(R.string.alternative_route_prefix_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.number_of_routes)) },
                    supportingText = { Text(stringResource(R.string.number_of_routes_range)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.distance_bounds),
                    style = MaterialTheme.typography.labelLarge,
                )
                RelativeValuesToggle(
                    label = stringResource(R.string.relative_distance_values),
                    checked = useRelativeDistanceValues,
                    onCheckedChange = { useRelativeDistanceValues = it },
                    enabled = !isGenerating,
                )
                AlternativeCriterionField(
                    value = minDistanceText,
                    onValueChange = { minDistanceText = it },
                    label = stringResource(
                        if (useRelativeDistanceValues) R.string.distance_under_meters
                        else R.string.minimum_distance_meters,
                    ),
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )
                AlternativeCriterionField(
                    value = maxDistanceText,
                    onValueChange = { maxDistanceText = it },
                    label = stringResource(
                        if (useRelativeDistanceValues) R.string.distance_over_meters
                        else R.string.maximum_distance_meters,
                    ),
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.score_bounds),
                    style = MaterialTheme.typography.labelLarge,
                )
                RelativeValuesToggle(
                    label = stringResource(R.string.relative_score_values),
                    checked = useRelativeScoreValues,
                    onCheckedChange = { useRelativeScoreValues = it },
                    enabled = !isGenerating,
                )
                AlternativeCriterionField(
                    value = minScoreText,
                    onValueChange = { minScoreText = it },
                    label = stringResource(
                        if (useRelativeScoreValues) R.string.points_under else R.string.minimum_points,
                    ),
                    enabled = !isGenerating,
                    integerOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AlternativeCriterionField(
                    value = maxScoreText,
                    onValueChange = { maxScoreText = it },
                    label = stringResource(
                        if (useRelativeScoreValues) R.string.points_over else R.string.maximum_points,
                    ),
                    enabled = !isGenerating,
                    integerOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text(
                    stringResource(R.string.empty_bounds_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (fieldsValid && !boundsValid) {
                    Text(
                        stringResource(R.string.invalid_alternative_route_bounds),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onGenerate(
                        AlternativeRouteCriteria(
                            count = requireNotNull(count),
                            sourceRouteId = sourceRoute.id,
                            minDistanceMeters = minDistance,
                            maxDistanceMeters = maxDistance,
                            minScore = minScore,
                            maxScore = maxScore,
                            useRelativeDistanceValues = useRelativeDistanceValues,
                            useRelativeScoreValues = useRelativeScoreValues,
                            fixedPrefixPointCount = splitAfterIndex + 1,
                        ),
                    )
                },
                enabled = fieldsValid && boundsValid && !isGenerating,
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.generate_routes))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isGenerating) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RelativeValuesToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AlternativeCriterionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    integerOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val allowed = input.filterIndexed { _, char ->
                char.isDigit() || (!integerOnly && (char == '.' || char == ','))
            }
            onValueChange(allowed.take(10))
        },
        label = { Text(label, maxLines = 2) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
        ),
        singleLine = true,
        enabled = enabled,
        modifier = modifier,
    )
}
