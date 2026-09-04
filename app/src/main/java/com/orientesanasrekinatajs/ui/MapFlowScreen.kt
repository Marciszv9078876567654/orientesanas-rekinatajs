package com.orientesanasrekinatajs.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.data.transfer.MapTransferRepository
import com.orientesanasrekinatajs.data.transfer.PdfMapDraft
import com.orientesanasrekinatajs.data.transfer.PdfRouteLayer
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.ui.components.pinnedRouteStrokeWidth
import com.orientesanasrekinatajs.ui.processing.MapProcessingUiState
import com.orientesanasrekinatajs.ui.routing.RouteMode
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction
import com.orientesanasrekinatajs.ui.routing.RouteRestrictionAction
import com.orientesanasrekinatajs.ui.routing.AlternativeRouteCriteria
import com.orientesanasrekinatajs.ui.routing.RoutingUiState
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class MapPage { EDIT, ROUTE }

@Composable
internal fun MapFlowScreen(
    processingState: MapProcessingUiState,
    routingState: RoutingUiState,
    defaultBudgetMeters: Float,
    showPointsPerKilometer: Boolean,
    showUsageTips: Boolean,
    onUsageTipDismissed: () -> Unit,
    onApplyEditedBoundary: (MapBoundary, Point2D?, Point2D?, Float?) -> Unit,
    onUpdateControlPoint: (ControlPoint) -> Unit,
    onAddControlPoint: (ControlPoint) -> Unit,
    onRemoveControlPoint: (String) -> Unit,
    onClearControlPoints: () -> Unit,
    onInvalidateRoute: () -> Unit,
    onCalculateRoute: (Float, RouteMode, Float?, Int?) -> Unit,
    onGenerateAlternativeRoutes: (List<ControlPoint>, Float, AlternativeRouteCriteria) -> Unit,
    onManageRoutes: (RouteManagementAction) -> Unit,
    onManageRouteRestrictions: (RouteRestrictionAction) -> Unit,
    onExportMap: (Uri, SavedMapDraft, Boolean) -> Unit,
    onExportPdf: (Uri, PdfMapDraft) -> Unit,
    onSaveMap: (SavedMapDraft, (ScannedMapEntity) -> Unit) -> Unit,
    onRenameSavedMap: (String, String, (ScannedMapEntity) -> Unit) -> Unit,
    onDeleteSavedMap: (String, () -> Unit) -> Unit,
    onReloadSavedMap: (String) -> Unit,
    isSavingMap: Boolean,
    isTransferringMap: Boolean,
    rotation: Int,
    rotationOffsetDegrees: Float,
    rotationGesturesEnabled: Boolean,
    onRotationGesture: (Float) -> Unit,
    onSnapRotation: () -> Unit,
    onRotationChange: (Int) -> Unit,
    onBackHome: () -> Unit,
) {
    val sessionKey = processingState.savedMapId ?: processingState.sourceUri?.toString()
        ?: processingState.rectifiedBitmap?.hashCode()?.toString()
    var page by rememberSaveable(sessionKey) {
        mutableStateOf(
            if (processingState.savedMapId != null || routingState.route != null) {
                MapPage.ROUTE
            } else {
                MapPage.EDIT
            },
        )
    }
    val rectified = requireNotNull(processingState.rectifiedBitmap)
    var lineStart by remember(rectified) { mutableStateOf(processingState.savedLineStart) }
    var lineEnd by remember(rectified) { mutableStateOf(processingState.savedLineEnd) }
    var lineDistanceText by rememberSaveable(sessionKey, rectified.hashCode()) {
        mutableStateOf(processingState.savedLineDistanceMeters?.toString().orEmpty())
    }
    var savedMapId by rememberSaveable(sessionKey) { mutableStateOf(processingState.savedMapId) }
    var savedMapName by rememberSaveable(sessionKey) { mutableStateOf(processingState.savedMapName) }
    var routeEntryKey by rememberSaveable(sessionKey) { mutableIntStateOf(0) }
    var awaitingRouteGeneration by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var mapEditChangedSinceEntry by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var editOpenedFromRoute by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var isDirty by rememberSaveable(sessionKey) {
        mutableStateOf(processingState.savedMapId == null || processingState.savedContentDirty)
    }
    val currentLineStart = lineStart
    val currentLineEnd = lineEnd
    val linePixels = if (currentLineStart != null && currentLineEnd != null) {
        hypot(
            currentLineEnd.x - currentLineStart.x,
            currentLineEnd.y - currentLineStart.y,
        )
    } else {
        0f
    }
    val lineMeters = lineDistanceText.localizedFloatOrNull()
    val effectivePixelsPerMeter = if (linePixels > 0f && lineMeters != null && lineMeters > 0f) {
        linePixels / lineMeters
    } else {
        processingState.savedPixelsPerMeter
    }
    LaunchedEffect(routingState.route) {
        if (routingState.route != null) page = MapPage.ROUTE
    }
    BackHandler(enabled = page == MapPage.ROUTE, onBack = onBackHome)
    val visiblePage = page
    val animationsEnabled = LocalAnimationsEnabled.current
    val defaultRouteName = stringResource(R.string.route)
    val createDraft: (
        selectedRoute: OptimizedRoute?,
        existingId: String?,
        name: String?,
        includeRoutes: Boolean,
    ) -> SavedMapDraft? = { selectedRoute, existingId, name, includeRoutes ->
        effectivePixelsPerMeter?.let { scale ->
            SavedMapDraft(
                bitmap = rectified,
                pixelsPerMeter = scale,
                points = processingState.controlPoints,
                route = routingState.route.takeIf { includeRoutes },
                selectedRoute = selectedRoute.takeIf { includeRoutes },
                alternativeRoutes = routingState.alternativeRoutes.takeIf { includeRoutes }.orEmpty(),
                routeMetadata = routingState.routeMetadata.takeIf { includeRoutes }.orEmpty(),
                routeRestrictions = routingState.routeRestrictions.takeIf { includeRoutes }.orEmpty(),
                routeMode = routingState.mode.name,
                routeBudgetMeters = routingState.budgetMeters.takeIf { includeRoutes },
                routeTargetScore = routingState.targetScore.takeIf { includeRoutes },
                lineStart = lineStart,
                lineEnd = lineEnd,
                lineDistanceMeters = lineMeters,
                rotationQuarterTurns = nearestQuarterTurn(rotation, rotationOffsetDegrees),
                existingId = existingId,
                name = name,
            )
        }
    }
    var showExportOptions by rememberSaveable { mutableStateOf(false) }
    var pendingExportIncludesRoutes by rememberSaveable { mutableStateOf(false) }
    var pendingPdfQuality by rememberSaveable { mutableIntStateOf(80) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MapTransferRepository.MIME_TYPE),
    ) { uri ->
        uri?.let { destination ->
            val routes = listOfNotNull(routingState.route) + routingState.alternativeRoutes
            val selected = routes.firstOrNull { it.id == routingState.selectedRouteId }
                ?: routingState.route
            createDraft(
                selected,
                null,
                savedMapName?.takeIf(String::isNotBlank) ?: defaultRouteName,
                pendingExportIncludesRoutes,
            )?.let { draft ->
                onExportMap(destination, draft, pendingExportIncludesRoutes)
            }
        }
    }
    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(MapTransferRepository.PDF_MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            val higherScoreRoutes = routingState.alternativeRoutes.take(
                routingState.nextLongestRouteCount,
            )
            val lowerScoreRoutes = routingState.alternativeRoutes.drop(
                routingState.nextLongestRouteCount,
            )
            val availableRoutes = higherScoreRoutes + listOfNotNull(routingState.route) +
                lowerScoreRoutes
            val selectableRoutes = availableRoutes.filterNot { candidate ->
                routingState.routeMetadata[candidate.id]?.isHidden == true
            }
            val activeRouteId = routingState.selectedRouteId
                ?.takeIf { id -> selectableRoutes.any { it.id == id } }
                ?: selectableRoutes.firstOrNull { candidate ->
                    candidate.path.map(ControlPoint::id) == routingState.selectedRoutePointIds
                }?.id
                ?: selectableRoutes.firstOrNull()?.id
            val portrayedCandidates = availableRoutes.mapNotNull { candidate ->
                val metadata = routingState.routeMetadata[candidate.id] ?: RouteMetadata()
                val isActive = candidate.id == activeRouteId
                if (!isActive && !metadata.isDisplayed) return@mapNotNull null
                candidate to metadata
            }
            val pinnedCandidateIds = portrayedCandidates.mapNotNull { (candidate, _) ->
                candidate.id.takeUnless { it == activeRouteId }
            }
            val portrayedRoutes = portrayedCandidates.map { (candidate, metadata) ->
                val isActive = candidate.id == activeRouteId
                PdfRouteLayer(
                    points = candidate.path,
                    colorIndex = metadata.colorIndex,
                    isActive = isActive,
                    isPinned = !isActive,
                    strokeWidth = if (isActive) {
                        7f
                    } else {
                        pinnedRouteStrokeWidth(
                            pinnedCandidateIds.indexOf(candidate.id),
                            pinnedCandidateIds.size,
                        )
                    },
                )
            }
            onExportPdf(
                uri,
                PdfMapDraft(
                    bitmap = rectified,
                    points = processingState.controlPoints,
                    routes = portrayedRoutes,
                    rotationDegrees = rotation * 90f + rotationOffsetDegrees,
                    imageQuality = pendingPdfQuality,
                ),
            )
        }
    }
    val requestExport: () -> Unit = { showExportOptions = true }
    val saveCurrentMap: (OptimizedRoute?, Boolean) -> Unit = { selectedRoute, saveCopy ->
        val copyName = "${savedMapName?.takeIf(String::isNotBlank) ?: defaultRouteName} - copy"
        createDraft(
            selectedRoute,
            savedMapId.takeUnless { saveCopy },
            if (saveCopy) copyName else savedMapName,
            true,
        )?.let { draft ->
            onSaveMap(draft) { saved ->
                savedMapId = saved.id
                savedMapName = saved.name
                isDirty = false
            }
        }
    }

    Crossfade(
        targetState = visiblePage,
        animationSpec = tween(if (animationsEnabled) 180 else 0),
        label = "mapPage",
    ) { targetPage ->
    if (targetPage == MapPage.ROUTE) {
        RouteScreen(
            bitmap = requireNotNull(processingState.rectifiedBitmap),
            route = routingState.route.takeUnless { awaitingRouteGeneration },
            alternativeRoutes = routingState.alternativeRoutes.takeUnless { awaitingRouteGeneration }.orEmpty(),
            nextLongestRouteCount = routingState.nextLongestRouteCount.takeUnless {
                awaitingRouteGeneration
            } ?: 0,
            preferredSelectedRoutePointIds = routingState.selectedRoutePointIds,
            preferredSelectedRouteId = routingState.selectedRouteId,
            allPoints = processingState.controlPoints,
            pixelsPerMeter = effectivePixelsPerMeter,
            rotation = rotation,
            rotationOffsetDegrees = rotationOffsetDegrees,
            rotationGesturesEnabled = rotationGesturesEnabled,
            onRotationGesture = { change -> isDirty = true; onRotationGesture(change) },
            onSnapRotation = { isDirty = true; onSnapRotation() },
            onRotationChange = onRotationChange,
            canSave = effectivePixelsPerMeter != null,
            isSaving = isSavingMap,
            isExporting = isTransferringMap,
            savedMapId = savedMapId,
            savedMapName = savedMapName,
            isDirty = isDirty,
            onSave = saveCurrentMap,
            onExport = requestExport,
            onRouteSelectionChanged = { isDirty = true },
            onRename = { name ->
                savedMapId?.let { id ->
                    onRenameSavedMap(id, name) { renamed -> savedMapName = renamed.name }
                }
            },
            onDelete = {
                savedMapId?.let { id -> onDeleteSavedMap(id, onBackHome) }
            },
            onEdit = {
                mapEditChangedSinceEntry = false
                editOpenedFromRoute = true
                page = MapPage.EDIT
            },
            onHome = onBackHome,
            routeEntryKey = routeEntryKey,
            isCalculatingRoute = routingState.isCalculating,
            routeMode = routingState.mode,
            routeBudgetMeters = routingState.budgetMeters,
            routeTargetScore = routingState.targetScore,
            routingError = routingState.error,
            defaultBudgetMeters = defaultBudgetMeters,
            showPointsPerKilometer = showPointsPerKilometer,
            canCalculateRoute = effectivePixelsPerMeter != null,
            onCalculatePrimaryRoute = { mode, budget, targetScore ->
                isDirty = true
                awaitingRouteGeneration = false
                effectivePixelsPerMeter?.let { scale ->
                    onCalculateRoute(scale, mode, budget, targetScore)
                }
            },
            isGeneratingAlternatives = routingState.isGeneratingAlternatives,
            onGenerateAlternatives = { criteria ->
                isDirty = true
                effectivePixelsPerMeter?.let { scale ->
                    onGenerateAlternativeRoutes(processingState.controlPoints, scale, criteria)
                }
            },
            routeMetadata = routingState.routeMetadata,
            routeRestrictions = routingState.routeRestrictions,
            onManageRoutes = { action ->
                isDirty = true
                onManageRoutes(action)
            },
            onManageRouteRestrictions = { action ->
                isDirty = true
                onManageRouteRestrictions(action)
            },
        )
    } else {
        EditMapScreen(
            processingState = processingState,
            showUsageTips = showUsageTips,
            onUsageTipDismissed = onUsageTipDismissed,
            onApplyBoundary = {
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                onApplyEditedBoundary(it, lineStart, lineEnd, lineMeters)
            },
            onUpdateControlPoint = {
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                onUpdateControlPoint(it)
            },
            onAddControlPoint = {
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                onAddControlPoint(it)
            },
            onRemoveControlPoint = {
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                onRemoveControlPoint(it)
            },
            onClearControlPoints = {
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                onClearControlPoints()
            },
            onOpenRoute = {
                val needsGeneration = routingState.route == null || mapEditChangedSinceEntry
                if (needsGeneration) routeEntryKey++
                awaitingRouteGeneration = needsGeneration
                editOpenedFromRoute = false
                page = MapPage.ROUTE
            },
            isSaving = isSavingMap,
            isDirty = isDirty,
            lineStart = lineStart,
            lineEnd = lineEnd,
            lineDistanceText = lineDistanceText,
            onLineChange = { start, end ->
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                lineStart = start
                lineEnd = end
            },
            onLineDistanceChange = {
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                lineDistanceText = it
            },
            rotation = rotation,
            rotationOffsetDegrees = rotationOffsetDegrees,
            rotationGesturesEnabled = rotationGesturesEnabled,
            onRotationGesture = { change -> isDirty = true; onRotationGesture(change) },
            onSnapRotation = { isDirty = true; onSnapRotation() },
            onRotationChange = onRotationChange,
            onCancel = if (editOpenedFromRoute) {
                {
                    awaitingRouteGeneration = false
                    mapEditChangedSinceEntry = false
                    editOpenedFromRoute = false
                    page = MapPage.ROUTE
                    savedMapId?.let(onReloadSavedMap)
                    Unit
                }
            } else {
                null
            },
            onBack = if (editOpenedFromRoute) {
                {
                    awaitingRouteGeneration = false
                    mapEditChangedSinceEntry = false
                    editOpenedFromRoute = false
                    page = MapPage.ROUTE
                    savedMapId?.let(onReloadSavedMap)
                    Unit
                }
            } else {
                onBackHome
            },
        )
    }
    }
    if (showExportOptions) {
        ExportMapDialog(
            hasRoutes = routingState.route != null || routingState.alternativeRoutes.isNotEmpty(),
            isExporting = isTransferringMap,
            onExport = { exportAsPdf, includeRoutes, pdfQuality ->
                pendingExportIncludesRoutes = includeRoutes
                pendingPdfQuality = pdfQuality
                showExportOptions = false
                val baseName = (savedMapName?.takeIf(String::isNotBlank) ?: defaultRouteName)
                    .replace(Regex("[^A-Za-z0-9._ -]"), "_")
                    .trim()
                    .ifBlank { "map" }
                if (exportAsPdf) {
                    pdfExportLauncher.launch("$baseName.pdf")
                } else {
                    exportLauncher.launch("$baseName.${MapTransferRepository.FILE_EXTENSION}")
                }
            },
            onDismiss = { showExportOptions = false },
        )
    }
}

@Composable
private fun ExportMapDialog(
    hasRoutes: Boolean,
    isExporting: Boolean,
    onExport: (Boolean, Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeRoutes by rememberSaveable(hasRoutes) { mutableStateOf(hasRoutes) }
    var exportAsPdf by rememberSaveable { mutableStateOf(false) }
    var pdfQuality by rememberSaveable { mutableFloatStateOf(80f) }
    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = { CenteredDialogTitle(stringResource(R.string.export_map)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.export_map_help))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.export_as_pdf), modifier = Modifier.weight(1f))
                    Switch(
                        checked = exportAsPdf,
                        onCheckedChange = { exportAsPdf = it },
                        enabled = !isExporting,
                    )
                }
                if (exportAsPdf) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(R.string.pdf_image_quality))
                            Text(stringResource(R.string.pdf_quality_format, pdfQuality.roundToInt()))
                        }
                        Slider(
                            value = pdfQuality,
                            onValueChange = { pdfQuality = it },
                            valueRange = 10f..100f,
                            steps = 8,
                            enabled = !isExporting,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.include_all_routes),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = includeRoutes,
                            onCheckedChange = { includeRoutes = it },
                            enabled = hasRoutes && !isExporting,
                        )
                    }
                }
                Text(
                    stringResource(
                        if (exportAsPdf) R.string.export_pdf_description
                        else if (includeRoutes && hasRoutes) R.string.export_map_and_routes_description
                        else R.string.export_map_only_description,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onExport(
                        exportAsPdf,
                        !exportAsPdf && includeRoutes && hasRoutes,
                        pdfQuality.roundToInt(),
                    )
                },
                enabled = !isExporting,
            ) {
                Text(stringResource(R.string.export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
