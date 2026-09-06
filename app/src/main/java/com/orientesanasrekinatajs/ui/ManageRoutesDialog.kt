package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Map as MapIcon
import androidx.compose.material.icons.outlined.Map as OutlinedMapIcon
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.ui.components.routeColor
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction

@Composable
internal fun ManageRoutesDialog(
    primaryRoute: OptimizedRoute?,
    alternativeRoutes: List<OptimizedRoute>,
    primaryRouteIndex: Int,
    routeMetadata: Map<String, RouteMetadata>,
    onAction: (RouteManagementAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val routes = primaryRoute?.let { primary ->
        val safePrimaryIndex = primaryRouteIndex.coerceIn(0, alternativeRoutes.size)
        alternativeRoutes.take(safePrimaryIndex) + primary + alternativeRoutes.drop(safePrimaryIndex)
    } ?: alternativeRoutes
    val initialRouteKeys = routes.map(OptimizedRoute::id)
    val primaryKey = primaryRoute?.id
    val defaultNames = routes.map { route ->
        route.id to managedRouteDefaultName(
            route = route,
            primaryRoute = primaryRoute,
            isPrimary = route.id == primaryKey,
        )
    }.toMap()
    val initialMetadata = initialRouteKeys.associateWith { key ->
        val metadata = routeMetadata[key] ?: RouteMetadata()
        metadata.copy(name = metadata.name.ifBlank { defaultNames.getValue(key) })
    }
    var managedRoutes by remember(initialRouteKeys) { mutableStateOf(routes) }
    var managedMetadata by remember(initialRouteKeys, routeMetadata) { mutableStateOf(initialMetadata) }
    var showDeleteUnstarredConfirmation by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var focusedRouteKey by remember { mutableStateOf<String?>(null) }
    var openMenuRouteKey by remember { mutableStateOf<String?>(null) }
    var pendingDeleteRouteKey by remember { mutableStateOf<String?>(null) }
    val hasUnsavedChanges = managedRoutes.map(OptimizedRoute::id) != initialRouteKeys ||
        managedMetadata != initialMetadata
    LaunchedEffect(focusedRouteKey) {
        if (focusedRouteKey == null) keyboardController?.hide()
    }
    fun clearInputFocus() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        focusedRouteKey = null
    }
    fun requestCancel() {
        clearInputFocus()
        if (hasUnsavedChanges) showDiscardConfirmation = true else onDismiss()
    }
    Dialog(
        onDismissRequest = {
            if (focusedRouteKey != null) clearInputFocus() else requestCancel()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .testTag("manage_routes_dialog_background")
                .pointerInput(focusManager) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        focusManager.clearFocus(force = true)
                    }
                }
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
            ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.manage_routes),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    managedRoutes.forEachIndexed { index, managedRoute ->
                        val key = managedRoute.id
                        val metadata = managedMetadata.getValue(key)
                        val duplicateName = stringResource(R.string.route_copy_name, metadata.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(12.dp))
                                .background(routeColor(metadata.colorIndex).copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                Modifier
                                    .width(7.dp)
                                    .height(56.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(routeColor(metadata.colorIndex)),
                            )
                            OutlinedTextField(
                                value = metadata.name,
                                onValueChange = { name ->
                                    managedMetadata = managedMetadata +
                                        (key to metadata.copy(name = name.take(60)))
                                },
                                label = { Text(stringResource(R.string.route_name), color = Color.White) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .offset(y = (-4).dp)
                                    .testTag("managed_route_name_$index")
                                    .onFocusChanged { state ->
                                        if (state.isFocused) {
                                            focusedRouteKey = key
                                        } else if (focusedRouteKey == key) {
                                            focusedRouteKey = null
                                        }
                                    },
                            )
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                IconButton(
                                    onClick = {
                                        clearInputFocus()
                                        managedRoutes = managedRoutes.toMutableList().also { items ->
                                            items[index] = items[index - 1]
                                            items[index - 1] = managedRoute
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = stringResource(R.string.move_route_up),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clearInputFocus()
                                        managedRoutes = managedRoutes.toMutableList().also { items ->
                                            items[index] = items[index + 1]
                                            items[index + 1] = managedRoute
                                        }
                                    },
                                    enabled = index < managedRoutes.lastIndex,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = stringResource(R.string.move_route_down),
                                    )
                                }
                            }
                            }
                            Box {
                                IconButton(onClick = { clearInputFocus(); openMenuRouteKey = key }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.route_actions))
                                }
                                DropdownMenu(
                                    expanded = openMenuRouteKey == key,
                                    onDismissRequest = { openMenuRouteKey = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (metadata.isStarred) R.string.unstar_route else R.string.star_route)) },
                                        leadingIcon = { Icon(if (metadata.isStarred) Icons.Default.Star else Icons.Default.StarBorder, null) },
                                        onClick = {
                                            managedMetadata = managedMetadata + (key to metadata.copy(isStarred = !metadata.isStarred))
                                            openMenuRouteKey = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.duplicate_route)) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                        onClick = {
                                            val duplicate = managedRoute.copy(id = java.util.UUID.randomUUID().toString())
                                            managedRoutes = managedRoutes.toMutableList().also { it.add(index + 1, duplicate) }
                                            val nextColor = com.orientesanasrekinatajs.domain.model.nextRouteColorIndex(managedMetadata.values)
                                            managedMetadata = managedMetadata + (duplicate.id to metadata.copy(
                                                name = duplicateName.take(60), isStarred = false,
                                                colorIndex = nextColor, isDisplayed = false,
                                            ))
                                            openMenuRouteKey = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (metadata.isHidden) R.string.unhide_route else R.string.hide_route)) },
                                        leadingIcon = { Icon(if (metadata.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) },
                                        onClick = {
                                            managedMetadata = managedMetadata + (key to metadata.copy(isHidden = !metadata.isHidden))
                                            openMenuRouteKey = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (metadata.isDisplayed) R.string.remove_route_from_map else R.string.display_route_on_map)) },
                                        leadingIcon = {
                                            Icon(
                                                if (metadata.isDisplayed) Icons.Default.MapIcon
                                                else Icons.Outlined.OutlinedMapIcon,
                                                null,
                                            )
                                        },
                                        onClick = {
                                            managedMetadata = managedMetadata + (key to metadata.copy(isDisplayed = !metadata.isDisplayed))
                                            openMenuRouteKey = null
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_route)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                        enabled = !metadata.isStarred,
                                        onClick = { pendingDeleteRouteKey = key; openMenuRouteKey = null },
                                    )
                                }
                            }
                        }
                    }
                    if (managedRoutes.isEmpty()) {
                        Text(
                            stringResource(R.string.no_routes_to_manage),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = {
                            clearInputFocus()
                            showDeleteUnstarredConfirmation = true
                        },
                        enabled = managedRoutes.any { managedMetadata[it.id]?.isStarred != true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null)
                        Text(
                            stringResource(R.string.delete_all_unstarred_routes),
                            modifier = Modifier.padding(start = 6.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = ::requestCancel) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = {
                            clearInputFocus()
                            onAction(
                                RouteManagementAction.Apply(
                                    orderedRoutes = managedRoutes,
                                    metadata = managedMetadata.mapValues { (key, metadata) ->
                                        metadata.copy(order = managedRoutes.indexOfFirst { it.id == key })
                                    },
                                ),
                            )
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
            }
            }
        }
    }
    if (showDeleteUnstarredConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_unstarred_routes_title),
            message = stringResource(R.string.delete_unstarred_routes_confirmation),
            onConfirm = {
                showDeleteUnstarredConfirmation = false
                val starredKeys = managedRoutes.map(OptimizedRoute::id)
                    .filter { key -> managedMetadata[key]?.isStarred == true }
                managedRoutes = managedRoutes.filter { it.id in starredKeys }
                managedMetadata = managedMetadata.filterKeys(starredKeys.toSet()::contains)
            },
            onDismiss = { showDeleteUnstarredConfirmation = false },
        )
    }
    pendingDeleteRouteKey?.let { routeKey ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_route_title),
            message = stringResource(
                R.string.delete_route_confirmation,
                managedMetadata[routeKey]?.name.orEmpty(),
            ),
            onConfirm = {
                pendingDeleteRouteKey = null
                managedRoutes = managedRoutes.filterNot { it.id == routeKey }
                managedMetadata = managedMetadata - routeKey
            },
            onDismiss = { pendingDeleteRouteKey = null },
        )
    }
    if (showDiscardConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.discard_route_management_changes_title),
            message = stringResource(R.string.discard_route_management_changes_confirmation),
            onConfirm = {
                showDiscardConfirmation = false
                onDismiss()
            },
            onDismiss = { showDiscardConfirmation = false },
        )
    }
}

@Composable
internal fun managedRouteDefaultName(
    route: OptimizedRoute,
    primaryRoute: OptimizedRoute?,
    isPrimary: Boolean,
): String {
    if (isPrimary || primaryRoute == null) return stringResource(R.string.primary_route_tab)
    val difference = route.totalScore - primaryRoute.totalScore
    return when {
        difference > 0 -> androidx.compose.ui.res.pluralStringResource(
            R.plurals.more_score_points_route_tab,
            difference,
            difference,
        )
        difference < 0 -> androidx.compose.ui.res.pluralStringResource(
            R.plurals.fewer_score_points_route_tab,
            -difference,
            -difference,
        )
        else -> stringResource(R.string.zero_score_points_route_tab)
    }
}
