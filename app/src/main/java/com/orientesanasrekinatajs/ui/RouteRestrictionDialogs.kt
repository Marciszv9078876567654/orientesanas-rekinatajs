package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.domain.model.RouteRestrictionType
import com.orientesanasrekinatajs.ui.routing.RouteRestrictionAction

@Composable
internal fun RouteRestrictionsDialog(
    points: List<ControlPoint>,
    restrictions: List<RouteRestriction>,
    onAction: (RouteRestrictionAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAddRestriction by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRestrictionId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeleteAllUnstarred by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(stringResource(R.string.route_restrictions)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.route_restrictions_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (restrictions.isEmpty()) {
                    Text(
                        stringResource(R.string.no_route_restrictions),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                restrictions.forEach { restriction ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                restrictionDescription(restriction, points),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(
                                onClick = { onAction(RouteRestrictionAction.ToggleStar(restriction.id)) },
                            ) {
                                Icon(
                                    if (restriction.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = stringResource(
                                        if (restriction.isStarred) R.string.unstar_rule else R.string.star_rule,
                                    ),
                                )
                            }
                            IconButton(
                                onClick = { pendingDeleteRestrictionId = restriction.id },
                                enabled = !restriction.isStarred,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_rule))
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showAddRestriction = true },
                    enabled = points.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.add_restriction))
                }
                OutlinedButton(
                    onClick = { confirmDeleteAllUnstarred = true },
                    enabled = restrictions.any { !it.isStarred },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Text(
                        stringResource(R.string.delete_all_unstarred_rules),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
    if (showAddRestriction) {
        AddRouteRestrictionDialog(
            points = points,
            onAdd = { restriction ->
                onAction(RouteRestrictionAction.Add(restriction))
                showAddRestriction = false
            },
            onDismiss = { showAddRestriction = false },
        )
    }
    if (pendingDeleteRestrictionId != null) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_route_restriction_title),
            message = stringResource(R.string.delete_route_restriction_confirmation),
            onConfirm = {
                pendingDeleteRestrictionId?.let { id ->
                    onAction(RouteRestrictionAction.Delete(id))
                }
                pendingDeleteRestrictionId = null
            },
            onDismiss = { pendingDeleteRestrictionId = null },
        )
    }
    if (confirmDeleteAllUnstarred) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_all_route_restrictions_title),
            message = stringResource(R.string.delete_all_route_restrictions_confirmation),
            onConfirm = {
                onAction(RouteRestrictionAction.DeleteAllUnstarred)
                confirmDeleteAllUnstarred = false
            },
            onDismiss = { confirmDeleteAllUnstarred = false },
        )
    }
}

@Composable
private fun AddRouteRestrictionDialog(
    points: List<ControlPoint>,
    onAdd: (RouteRestriction) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(RouteRestrictionType.BLACKLIST_CONTROL) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val eligiblePoints = if (type in setOf(
            RouteRestrictionType.BLACKLIST_CONTROL,
            RouteRestrictionType.MANDATORY_CONTROL,
        )
    ) points.filter { it.type == ControlPointType.CONTROL } else points
    var firstPointId by rememberSaveable(type) { mutableStateOf(eligiblePoints.firstOrNull()?.id.orEmpty()) }
    var secondPointId by rememberSaveable(type) {
        mutableStateOf(eligiblePoints.firstOrNull { it.id != firstPointId }?.id.orEmpty())
    }
    val needsSecondPoint = type in setOf(
        RouteRestrictionType.BLACKLIST_CONNECTION,
        RouteRestrictionType.MANDATORY_CONNECTION,
    )
    val valid = firstPointId.isNotBlank() && (!needsSecondPoint ||
        secondPointId.isNotBlank() && secondPointId != firstPointId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(stringResource(R.string.add_restriction)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(type.labelResource()), modifier = Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        RouteRestrictionType.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(stringResource(candidate.labelResource())) },
                                onClick = { type = candidate; typeMenuExpanded = false },
                            )
                        }
                    }
                }
                RestrictionPointDropdown(
                    label = stringResource(R.string.first_point),
                    points = eligiblePoints,
                    selectedPointId = firstPointId,
                    onSelected = { selected ->
                        firstPointId = selected
                        if (selected == secondPointId) {
                            secondPointId = eligiblePoints.firstOrNull { it.id != selected }?.id.orEmpty()
                        }
                    },
                )
                if (needsSecondPoint) {
                    RestrictionPointDropdown(
                        label = stringResource(R.string.second_point),
                        points = eligiblePoints.filterNot { it.id == firstPointId },
                        selectedPointId = secondPointId,
                        onSelected = { secondPointId = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        RouteRestriction(
                            type = type,
                            firstPointId = firstPointId,
                            secondPointId = secondPointId.takeIf { needsSecondPoint },
                        ),
                    )
                },
                enabled = valid,
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RestrictionPointDropdown(
    label: String,
    points: List<ControlPoint>,
    selectedPointId: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = points.firstOrNull { it.id == selectedPointId }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = points.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selected?.let(::restrictionPointLabel).orEmpty(), modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                points.forEach { point ->
                    DropdownMenuItem(
                        text = { Text(restrictionPointLabel(point)) },
                        onClick = { onSelected(point.id); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun restrictionDescription(
    restriction: RouteRestriction,
    points: List<ControlPoint>,
): String {
    val byId = points.associateBy(ControlPoint::id)
    val first = byId[restriction.firstPointId]?.let(::restrictionPointLabel) ?: "?"
    val second = restriction.secondPointId?.let { byId[it]?.let(::restrictionPointLabel) ?: "?" }
    return "${stringResource(restriction.type.labelResource())}: " +
        listOfNotNull(first, second).joinToString(" – ")
}

internal fun restrictionPointLabel(point: ControlPoint): String = when (point.type) {
    ControlPointType.START -> "S"
    ControlPointType.FINISH -> "F"
    ControlPointType.START_FINISH -> "S/F"
    ControlPointType.CONTROL -> point.code.toString()
}

private fun RouteRestrictionType.labelResource(): Int = when (this) {
    RouteRestrictionType.BLACKLIST_CONTROL -> R.string.blacklist_control
    RouteRestrictionType.BLACKLIST_CONNECTION -> R.string.blacklist_connection
    RouteRestrictionType.MANDATORY_CONTROL -> R.string.mandatory_control
    RouteRestrictionType.MANDATORY_CONNECTION -> R.string.mandatory_connection
}
