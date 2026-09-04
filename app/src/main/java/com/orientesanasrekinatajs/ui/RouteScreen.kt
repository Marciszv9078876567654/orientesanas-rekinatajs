package com.orientesanasrekinatajs.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.ui.components.RouteDetailsBottomSheet
import com.orientesanasrekinatajs.ui.components.RouteDetailsPanelState
import com.orientesanasrekinatajs.ui.components.RouteEditorBottomSheet
import com.orientesanasrekinatajs.ui.components.RouteEditorPanelState
import com.orientesanasrekinatajs.ui.components.RouteRenderingCanvas
import com.orientesanasrekinatajs.ui.components.RouteRenderLayer
import com.orientesanasrekinatajs.ui.components.routeColor
import com.orientesanasrekinatajs.ui.routing.RouteMode
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction
import com.orientesanasrekinatajs.ui.routing.RouteRestrictionAction
import com.orientesanasrekinatajs.ui.routing.AlternativeRouteCriteria
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled

private enum class RouteLeadingAction { HOME, CLOSE_DETAILS, CANCEL_EDIT }

@Composable
internal fun RouteScreen(
    bitmap: Bitmap,
    route: OptimizedRoute?,
    alternativeRoutes: List<OptimizedRoute>,
    nextLongestRouteCount: Int,
    preferredSelectedRoutePointIds: List<String>,
    preferredSelectedRouteId: String?,
    allPoints: List<ControlPoint>,
    pixelsPerMeter: Float?,
    rotation: Int,
    rotationOffsetDegrees: Float,
    rotationGesturesEnabled: Boolean,
    onRotationGesture: (Float) -> Unit,
    onSnapRotation: () -> Unit,
    onRotationChange: (Int) -> Unit,
    canSave: Boolean,
    isSaving: Boolean,
    isExporting: Boolean,
    savedMapId: String?,
    savedMapName: String?,
    isDirty: Boolean,
    onSave: (OptimizedRoute?, Boolean) -> Unit,
    onExport: () -> Unit,
    onRouteSelectionChanged: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onHome: () -> Unit,
    routeEntryKey: Int,
    isCalculatingRoute: Boolean,
    routeMode: RouteMode,
    routeBudgetMeters: Float?,
    routeTargetScore: Int?,
    routingError: String?,
    defaultBudgetMeters: Float,
    showPointsPerKilometer: Boolean,
    canCalculateRoute: Boolean,
    onCalculatePrimaryRoute: (RouteMode, Float?, Int?) -> Unit,
    isGeneratingAlternatives: Boolean,
    onGenerateAlternatives: (AlternativeRouteCriteria) -> Unit,
    routeMetadata: Map<String, RouteMetadata>,
    routeRestrictions: List<RouteRestriction>,
    onManageRoutes: (RouteManagementAction) -> Unit,
    onManageRouteRestrictions: (RouteRestrictionAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val density = LocalDensity.current
    val routeMenuGapPx = with(density) { 16.dp.roundToPx() }
    val routeMenuPositionProvider = remember(routeMenuGapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = anchorBounds.left.coerceIn(
                    0,
                    (windowSize.width - popupContentSize.width).coerceAtLeast(0),
                ),
                y = (anchorBounds.top - routeMenuGapPx - popupContentSize.height).coerceIn(
                    0,
                    (windowSize.height - popupContentSize.height).coerceAtLeast(0),
                ),
            )
        }
    }
    val mapActionsMarginPx = with(density) { 6.dp.roundToPx() }
    val mapActionsPositionProvider = remember(mapActionsMarginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val maximumX = (windowSize.width - popupContentSize.width - mapActionsMarginPx)
                    .coerceAtLeast(mapActionsMarginPx)
                val maximumY = (windowSize.height - popupContentSize.height - mapActionsMarginPx)
                    .coerceAtLeast(mapActionsMarginPx)
                return IntOffset(
                    x = (anchorBounds.right - popupContentSize.width - mapActionsMarginPx)
                        .coerceIn(mapActionsMarginPx, maximumX),
                    y = (anchorBounds.bottom + mapActionsMarginPx)
                        .coerceIn(mapActionsMarginPx, maximumY),
                )
            }
        }
    }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var routeDetailsPanelState by rememberSaveable {
        mutableStateOf(RouteDetailsPanelState.HALF)
    }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSaveConfirmation by rememberSaveable { mutableStateOf(false) }
    var showUnsavedHomeConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingHomeAfterSave by rememberSaveable { mutableStateOf(false) }
    var showMapActions by rememberSaveable { mutableStateOf(false) }
    var showRouteMenu by rememberSaveable { mutableStateOf(false) }
    var showRouteEditor by rememberSaveable { mutableStateOf(false) }
    var routeEditorPanelState by rememberSaveable {
        mutableStateOf(RouteEditorPanelState.HALF)
    }
    var routeEditorPath by remember { mutableStateOf<List<ControlPoint>?>(null) }
    var confirmRouteEditSave by rememberSaveable { mutableStateOf(false) }
    var confirmRouteEditCancel by rememberSaveable { mutableStateOf(false) }
    var showRouteRestrictions by rememberSaveable { mutableStateOf(false) }
    var showEditMapRouteWarning by rememberSaveable { mutableStateOf(false) }
    var showManageRoutes by rememberSaveable { mutableStateOf(false) }
    var showRouteGenerator by rememberSaveable(routeEntryKey) { mutableStateOf(route == null) }
    var routeGeneratorOpenedFromMenu by rememberSaveable(routeEntryKey) { mutableStateOf(false) }
    var requestedRouteMode by rememberSaveable { mutableStateOf(routeMode) }
    var waitingForPrimaryRoute by rememberSaveable { mutableStateOf(false) }
    var dismissedRoutingError by remember { mutableStateOf<String?>(null) }
    var showAlternativeRoutes by rememberSaveable { mutableStateOf(false) }
    var waitingForAlternatives by rememberSaveable { mutableStateOf(false) }
    var renameText by rememberSaveable(savedMapId, savedMapName) { mutableStateOf(savedMapName.orEmpty()) }
    BackHandler(enabled = showRouteEditor) {
        if (routeEditorPanelState == RouteEditorPanelState.MINIMIZED) {
            routeEditorPanelState = RouteEditorPanelState.HALF
        } else {
            confirmRouteEditCancel = true
        }
    }
    BackHandler(enabled = showDetails && !showRouteEditor) {
        routeDetailsPanelState = RouteDetailsPanelState.DISMISSED
    }
    val higherScoreRoutes = alternativeRoutes.take(nextLongestRouteCount)
    val lowerScoreRoutes = alternativeRoutes.drop(nextLongestRouteCount)
    val availableRoutes = higherScoreRoutes + listOfNotNull(route) + lowerScoreRoutes
    val selectableRoutes = availableRoutes.filterNot { routeMetadata[it.id]?.isHidden == true }
    val preferredRouteId = preferredSelectedRouteId
        ?.takeIf { selectedId -> selectableRoutes.any { it.id == selectedId } }
        ?: selectableRoutes.firstOrNull {
            it.path.map(ControlPoint::id) == preferredSelectedRoutePointIds
        }?.id
        ?: selectableRoutes.firstOrNull()?.id.orEmpty()
    val routeSelectionKey = route?.let { primary ->
        buildString {
            append(primary.path.joinToString(":") { it.id })
            append('|').append(primary.totalScore)
            append('|').append(primary.totalDistanceMeters)
        }
    } ?: "empty-route"
    var selectedRouteId by rememberSaveable(routeSelectionKey) {
        mutableStateOf(preferredRouteId)
    }
    LaunchedEffect(preferredRouteId, selectableRoutes.map(OptimizedRoute::id)) {
        selectedRouteId = preferredRouteId
    }
    val safeSelectedRouteIndex = availableRoutes.indexOfFirst { candidate ->
        routeMetadata[candidate.id]?.isHidden != true &&
        candidate.id == selectedRouteId
    }.takeIf { it >= 0 } ?: -1
    val displayedRoute = availableRoutes.getOrNull(safeSelectedRouteIndex)
    LaunchedEffect(isGeneratingAlternatives) {
        if (!isGeneratingAlternatives && waitingForAlternatives) {
            waitingForAlternatives = false
            showAlternativeRoutes = false
            routeDetailsPanelState = RouteDetailsPanelState.HALF
            showDetails = true
        }
    }
    LaunchedEffect(isCalculatingRoute) {
        if (!isCalculatingRoute && waitingForPrimaryRoute) {
            waitingForPrimaryRoute = false
            showRouteGenerator = false
        }
    }
    LaunchedEffect(isSaving, isDirty, pendingHomeAfterSave) {
        if (!isSaving && !isDirty) {
            showUnsavedHomeConfirmation = false
            if (pendingHomeAfterSave) {
                pendingHomeAfterSave = false
                onHome()
            }
        } else if (!isSaving && pendingHomeAfterSave) {
            pendingHomeAfterSave = false
            showUnsavedHomeConfirmation = true
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                IconButton(
                    onClick = {
                        if (showRouteEditor) {
                            confirmRouteEditCancel = true
                        } else if (showDetails) {
                            routeDetailsPanelState = RouteDetailsPanelState.DISMISSED
                        } else if (isSaving) {
                            pendingHomeAfterSave = true
                        } else if (isDirty || savedMapId == null) {
                            showUnsavedHomeConfirmation = true
                        } else {
                            onHome()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    val leadingAction = when {
                        showRouteEditor -> RouteLeadingAction.CANCEL_EDIT
                        showDetails -> RouteLeadingAction.CLOSE_DETAILS
                        else -> RouteLeadingAction.HOME
                    }
                    Crossfade(
                        targetState = leadingAction,
                        animationSpec = tween(if (animationsEnabled) 120 else 0),
                        label = "routeLeadingAction",
                    ) { action ->
                        Icon(
                            imageVector = if (action == RouteLeadingAction.HOME) {
                                Icons.Default.Home
                            } else {
                                Icons.Default.Close
                            },
                            contentDescription = stringResource(
                                when (action) {
                                    RouteLeadingAction.HOME -> R.string.home
                                    RouteLeadingAction.CLOSE_DETAILS -> R.string.close
                                    RouteLeadingAction.CANCEL_EDIT -> R.string.cancel
                                },
                            ),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = when {
                            savedMapId != null && isDirty -> stringResource(
                                R.string.route_existing_unsaved_title,
                                savedMapName.orEmpty(),
                            )
                            savedMapId == null -> stringResource(R.string.route_unsaved)
                            else -> stringResource(R.string.route_saved_title, savedMapName.orEmpty())
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Box(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { showMapActions = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.map_actions))
                    }
                    if (showMapActions) {
                        Popup(
                            popupPositionProvider = mapActionsPositionProvider,
                            onDismissRequest = { showMapActions = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            MapDropdownContent {
                                Surface(
                                    modifier = Modifier.width(IntrinsicSize.Max),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 8.dp,
                                ) {
                                    Column {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.rename_route)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.DriveFileRenameOutline,
                                                    contentDescription = null,
                                                )
                                            },
                                            enabled = savedMapId != null,
                                            onClick = { showMapActions = false; showRename = true },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.save_map)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Save, contentDescription = null)
                                            },
                                            enabled = canSave && !isSaving,
                                            onClick = {
                                                showMapActions = false
                                                showSaveConfirmation = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.delete_saved_route))
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                            },
                                            enabled = savedMapId != null && !isDirty,
                                            onClick = {
                                                showMapActions = false
                                                showDeleteConfirmation = true
                                            },
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.edit_map)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                            },
                                            onClick = {
                                                showMapActions = false
                                                if (availableRoutes.isNotEmpty()) {
                                                    showEditMapRouteWarning = true
                                                } else {
                                                    onEdit()
                                                }
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.export_map)) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Share, contentDescription = null)
                                            },
                                            enabled = canSave && !isExporting,
                                            onClick = { showMapActions = false; onExport() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            RouteRenderingCanvas(
                bitmap = bitmap,
                route = routeEditorPath ?: displayedRoute?.path.orEmpty(),
                routeLayers = if (selectableRoutes.isEmpty()) emptyList() else availableRoutes.mapNotNull { candidate ->
                    val metadata = routeMetadata[candidate.id] ?: RouteMetadata()
                    val isActive = candidate.id == displayedRoute?.id
                    if (!isActive && !metadata.isDisplayed) return@mapNotNull null
                    RouteRenderLayer(
                        id = candidate.id,
                        points = if (isActive) routeEditorPath ?: candidate.path else candidate.path,
                        color = routeColor(metadata.colorIndex),
                        isActive = isActive,
                        patternIndex = metadata.colorIndex,
                    )
                },
                allPoints = allPoints,
                rotationQuarterTurns = rotation,
                rotationOffsetDegrees = rotationOffsetDegrees,
                rotationGesturesEnabled = rotationGesturesEnabled,
                onRotationGesture = onRotationGesture,
                recenterKey = recenterKey,
                onMapTap = {
                    if (
                        showRouteEditor &&
                        routeEditorPanelState != RouteEditorPanelState.MINIMIZED
                    ) {
                        routeEditorPanelState = RouteEditorPanelState.MINIMIZED
                    }
                    if (
                        showDetails &&
                        routeDetailsPanelState != RouteDetailsPanelState.MINIMIZED
                    ) {
                        routeDetailsPanelState = RouteDetailsPanelState.MINIMIZED
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            MapOverlayButtons(
                onRotate = {
                    onRotationChange(nextClockwiseQuarterTurn(rotation, rotationOffsetDegrees))
                },
                onRecenter = { onSnapRotation(); recenterKey++ },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
            if (isCalculatingRoute) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(
                            stringResource(R.string.generating_route),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                displayedRoute?.let { selected ->
                    stringResource(
                        R.string.route_summary,
                        selected.totalDistanceMeters,
                        selected.totalScore,
                    )
                } ?: stringResource(R.string.no_route_generated),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(
                        onClick = { showRouteMenu = true },
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Default.Route, contentDescription = stringResource(R.string.route_actions))
                    }
                    if (showRouteMenu) {
                        Popup(
                            popupPositionProvider = routeMenuPositionProvider,
                            onDismissRequest = { showRouteMenu = false },
                            properties = PopupProperties(focusable = true),
                        ) {
                            MapDropdownContent {
                                Surface(
                                    modifier = Modifier.width(IntrinsicSize.Max),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 8.dp,
                                ) {
                                    Column {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.generate_route)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Route, contentDescription = null)
                                        },
                                        onClick = {
                                            showRouteMenu = false
                                            routeGeneratorOpenedFromMenu = true
                                            showRouteGenerator = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.route_restrictions)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.DeleteForever, contentDescription = null)
                                        },
                                        onClick = {
                                            showRouteMenu = false
                                            showRouteRestrictions = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.alternative_routes)) },
                                        enabled = route != null && availableRoutes.any {
                                            routeMetadata[it.id]?.isHidden != true
                                        },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null)
                                        },
                                        onClick = {
                                            showRouteMenu = false
                                            showAlternativeRoutes = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit_route)) },
                                        enabled = displayedRoute != null && pixelsPerMeter != null,
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                        onClick = {
                                            showRouteMenu = false
                                            showDetails = false
                                            routeEditorPath = displayedRoute?.path
                                            routeEditorPanelState = RouteEditorPanelState.HALF
                                            showRouteEditor = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.manage_routes)) },
                                        enabled = route != null || alternativeRoutes.isNotEmpty(),
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null)
                                        },
                                        onClick = {
                                            showRouteMenu = false
                                            showManageRoutes = true
                                        },
                                    )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        routeDetailsPanelState = RouteDetailsPanelState.HALF
                        showDetails = true
                    },
                    enabled = route != null && availableRoutes.any {
                        routeMetadata[it.id]?.isHidden != true
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(stringResource(R.string.show_route_details))
                }
            }
        }
    }

    if (showRouteEditor && displayedRoute != null && routeEditorPath != null) {
        RouteEditorBottomSheet(
            route = displayedRoute,
            editedPath = requireNotNull(routeEditorPath),
            allPoints = allPoints,
            panelState = routeEditorPanelState,
            onPanelStateChange = { routeEditorPanelState = it },
            onPathChange = { routeEditorPath = it },
            onSaveRequest = { confirmRouteEditSave = true },
            onCancelRequest = { confirmRouteEditCancel = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    if (showDetails && route != null && availableRoutes.any {
        routeMetadata[it.id]?.isHidden != true
    }) {
        RouteDetailsBottomSheet(
            route = route,
            alternativeRoutes = alternativeRoutes,
            nextLongestRouteCount = nextLongestRouteCount,
            routeMetadata = routeMetadata,
            selectedRouteId = selectedRouteId,
            showPointsPerKilometer = showPointsPerKilometer,
            panelState = routeDetailsPanelState,
            onPanelStateChange = { routeDetailsPanelState = it },
            onRouteSelected = { newRouteId ->
                if (newRouteId != selectedRouteId) {
                    selectedRouteId = newRouteId
                    onManageRoutes(RouteManagementAction.Select(newRouteId))
                    onRouteSelectionChanged()
                }
            },
            onDismissRequest = { showDetails = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
    if (confirmRouteEditSave && displayedRoute != null) {
        ConfirmationDialog(
            title = stringResource(R.string.save_route_edit_title),
            message = stringResource(R.string.save_route_edit_confirmation),
            onConfirm = {
                pixelsPerMeter?.let { scale ->
                    onManageRoutes(
                        RouteManagementAction.UpdatePath(
                            displayedRoute.id,
                            routeEditorPath.orEmpty(),
                            scale,
                        ),
                    )
                }
                confirmRouteEditSave = false
                showRouteEditor = false
                routeEditorPath = null
            },
            onDismiss = { confirmRouteEditSave = false },
        )
    }
    if (confirmRouteEditCancel) {
        ConfirmationDialog(
            title = stringResource(R.string.cancel_route_edit_title),
            message = stringResource(R.string.cancel_route_edit_confirmation),
            onConfirm = {
                confirmRouteEditCancel = false
                showRouteEditor = false
                routeEditorPath = null
            },
            onDismiss = { confirmRouteEditCancel = false },
        )
    }
    if (showRouteRestrictions) {
        RouteRestrictionsDialog(
            points = allPoints,
            restrictions = routeRestrictions,
            onAction = onManageRouteRestrictions,
            onDismiss = { showRouteRestrictions = false },
        )
    }
    if (showAlternativeRoutes && route != null && availableRoutes.any {
        routeMetadata[it.id]?.isHidden != true
    }) {
        AlternativeRoutesDialog(
            routes = availableRoutes,
            initialSourceRouteId = displayedRoute?.id ?: route.id,
            routeMetadata = routeMetadata,
            onSourceSelected = { newRouteId ->
                if (newRouteId != selectedRouteId) {
                    selectedRouteId = newRouteId
                    onManageRoutes(RouteManagementAction.Select(newRouteId))
                    onRouteSelectionChanged()
                }
            },
            isGenerating = isGeneratingAlternatives,
            onGenerate = { criteria ->
                waitingForAlternatives = true
                onGenerateAlternatives(criteria)
            },
            onDismiss = { showAlternativeRoutes = false },
        )
    }
    if (showRouteGenerator) {
        RouteGenerationDialog(
            initialMode = requestedRouteMode,
            initialBudgetMeters = routeBudgetMeters ?: defaultBudgetMeters,
            initialTargetScore = routeTargetScore ?: 10,
            isCalculating = isCalculatingRoute,
            canCalculate = canCalculateRoute,
            dismissAsCancel = routeGeneratorOpenedFromMenu,
            onGenerate = { mode, budget, targetScore ->
                dismissedRoutingError = null
                requestedRouteMode = mode
                showRouteGenerator = false
                waitingForPrimaryRoute = true
                onCalculatePrimaryRoute(mode, budget, targetScore)
            },
            onCreateEmpty = {
                pixelsPerMeter?.let { scale ->
                    showRouteGenerator = false
                    waitingForPrimaryRoute = false
                    onManageRoutes(RouteManagementAction.CreateEmpty(allPoints, scale))
                }
            },
            onDismiss = { if (!isCalculatingRoute) showRouteGenerator = false },
        )
    }
    if (showEditMapRouteWarning) {
        ConfirmationDialog(
            title = stringResource(R.string.edit_map_removes_route_title),
            message = stringResource(R.string.edit_map_removes_route_confirmation),
            onConfirm = {
                showEditMapRouteWarning = false
                onEdit()
            },
            onDismiss = { showEditMapRouteWarning = false },
        )
    }
    if (showManageRoutes) {
        ManageRoutesDialog(
            primaryRoute = route,
            alternativeRoutes = alternativeRoutes,
            primaryRouteIndex = nextLongestRouteCount,
            routeMetadata = routeMetadata,
            onAction = onManageRoutes,
            onDismiss = { showManageRoutes = false },
        )
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            modifier = Modifier.clearFocusOnPointerDown(focusManager),
            title = { CenteredDialogTitle(stringResource(R.string.rename_route)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(60) },
                    label = { Text(stringResource(R.string.route_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRename(renameText); showRename = false },
                    enabled = renameText.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (showDeleteConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_saved_route_title),
            message = stringResource(R.string.delete_saved_route_confirmation),
            onConfirm = { showDeleteConfirmation = false; onDelete() },
            onDismiss = { showDeleteConfirmation = false },
        )
    }
    if (showUnsavedHomeConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.leave_unsaved_route_title),
            message = stringResource(R.string.leave_unsaved_route_confirmation),
            onConfirm = { showUnsavedHomeConfirmation = false; onHome() },
            onDismiss = { showUnsavedHomeConfirmation = false },
        )
    }
    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isSaving) showSaveConfirmation = false },
            title = { CenteredDialogTitle(stringResource(R.string.save_map_confirmation_title)) },
            text = { Text(stringResource(R.string.save_map_confirmation_message)) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (savedMapId != null) {
                        TextButton(
                            onClick = {
                                showSaveConfirmation = false
                                onSave(displayedRoute, true)
                            },
                            enabled = !isSaving,
                        ) { Text(stringResource(R.string.save_copy)) }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { showSaveConfirmation = false },
                        enabled = !isSaving,
                    ) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = {
                            showSaveConfirmation = false
                            onSave(displayedRoute, false)
                        },
                        enabled = !isSaving,
                    ) { Text(stringResource(R.string.save)) }
                }
            },
        )
    }
    routingError?.takeUnless { it == dismissedRoutingError }?.let { error ->
        MessageDialog(
            title = stringResource(R.string.route_error_title),
            message = error,
            onDismiss = { dismissedRoutingError = error },
        )
    }
}

@Composable
private fun MapDropdownContent(content: @Composable () -> Unit) {
    val animationsEnabled = LocalAnimationsEnabled.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val opacity by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (animationsEnabled) 90 else 0),
        label = "mapDropdownOpacity",
    )
    Box(Modifier.graphicsLayer { alpha = opacity }) {
        content()
    }
}

@Composable
private fun RouteGenerationDialog(
    initialMode: RouteMode,
    initialBudgetMeters: Float,
    initialTargetScore: Int,
    isCalculating: Boolean,
    canCalculate: Boolean,
    dismissAsCancel: Boolean,
    onGenerate: (RouteMode, Float?, Int?) -> Unit,
    onCreateEmpty: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var mode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var budgetText by rememberSaveable(initialBudgetMeters) { mutableStateOf(initialBudgetMeters.toString()) }
    var targetScoreText by rememberSaveable(initialTargetScore) { mutableStateOf(initialTargetScore.toString()) }
    val budget = budgetText.localizedFloatOrNull()
    val targetScore = targetScoreText.toIntOrNull()
    val parametersValid = when (mode) {
        RouteMode.SHORTEST -> true
        RouteMode.BEST_SCORE -> budget?.let { it > 0f } == true
        RouteMode.TARGET_SCORE -> targetScore?.let { it > 0 } == true
    }
    AlertDialog(
        onDismissRequest = { if (!isCalculating) onDismiss() },
        modifier = Modifier.clearFocusOnPointerDown(focusManager),
        text = {
            Column(
                modifier = Modifier.clearFocusOnPointerDown(focusManager),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.choose_route_generation),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    RouteMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            enabled = !isCalculating,
                            label = {
                                Text(
                                    routeModeLabel(option),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onCreateEmpty,
                        enabled = !isCalculating,
                        label = {
                            Text(
                                stringResource(R.string.empty_route),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode == RouteMode.BEST_SCORE) {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it },
                        label = { Text(stringResource(R.string.distance_budget), color = Color.White) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        enabled = !isCalculating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (mode == RouteMode.TARGET_SCORE) {
                    OutlinedTextField(
                        value = targetScoreText,
                        onValueChange = { targetScoreText = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.target_score), color = Color.White) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        enabled = !isCalculating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (isCalculating) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        Text(
                            stringResource(R.string.generating_route),
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onGenerate(
                        mode,
                        budget.takeIf { mode == RouteMode.BEST_SCORE },
                        targetScore.takeIf { mode == RouteMode.TARGET_SCORE },
                    )
                },
                enabled = canCalculate && parametersValid && !isCalculating,
            ) {
                if (isCalculating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.generate_route))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCalculating) {
                Text(stringResource(if (dismissAsCancel) R.string.cancel else R.string.skip))
            }
        },
    )
}

@Composable
private fun routeModeLabel(mode: RouteMode): String = stringResource(
    when (mode) {
        RouteMode.SHORTEST -> R.string.route_mode_shortest
        RouteMode.BEST_SCORE -> R.string.route_mode_score
        RouteMode.TARGET_SCORE -> R.string.route_mode_target_score
    },
)
