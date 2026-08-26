package com.orientesanasrekinatajs.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import com.orientesanasrekinatajs.domain.model.UserPreferences
import com.orientesanasrekinatajs.ui.components.DistanceCalibrationCanvas
import com.orientesanasrekinatajs.ui.components.ImageSourceScreen
import com.orientesanasrekinatajs.ui.components.InteractiveCornerCanvas
import com.orientesanasrekinatajs.ui.components.InteractiveControlPointCanvas
import com.orientesanasrekinatajs.ui.components.RouteDetailsBottomSheet
import com.orientesanasrekinatajs.ui.components.RouteRenderingCanvas
import com.orientesanasrekinatajs.ui.processing.MapProcessingStage
import com.orientesanasrekinatajs.ui.processing.MapProcessingUiState
import com.orientesanasrekinatajs.ui.routing.RouteMode
import com.orientesanasrekinatajs.ui.routing.RoutingUiState
import com.orientesanasrekinatajs.ui.savedmaps.SavedMapsEvent
import com.orientesanasrekinatajs.ui.savedmaps.SavedMapsUiState
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import java.text.DateFormat
import java.util.Date
import kotlin.math.hypot

@Composable
fun OrienteeringApp(
    processingState: MapProcessingUiState,
    routingState: RoutingUiState,
    userPreferences: UserPreferences,
    savedMapsState: SavedMapsUiState,
    onImageSelected: (Uri) -> Unit,
    onApplyBoundary: (MapBoundary) -> Unit,
    onApplyEditedBoundary: (MapBoundary, Point2D?, Point2D?, Float?) -> Unit,
    onUpdateControlPoint: (ControlPoint) -> Unit,
    onAddControlPoint: (ControlPoint) -> Unit,
    onRemoveControlPoint: (String) -> Unit,
    onClearControlPoints: () -> Unit,
    onCalculateRoute: (
        pixelsPerMeter: Float,
        mode: RouteMode,
        budgetMeters: Float?,
        targetScore: Int?,
    ) -> Unit,
    onSaveMap: (SavedMapDraft, (ScannedMapEntity) -> Unit) -> Unit,
    onLoadSavedMap: (String) -> Unit,
    onRenameSavedMap: (String, String, (ScannedMapEntity) -> Unit) -> Unit,
    onDeleteSavedMap: (String, () -> Unit) -> Unit,
    onClearAllSavedMaps: () -> Unit,
    onDismissSavedMapsEvent: () -> Unit,
    onReset: () -> Unit,
    onUpdateTheme: (ThemeConfig) -> Unit,
    onUpdateLanguage: (LanguageConfig) -> Unit,
    onUpdateAnimations: (Boolean) -> Unit,
    onUpdateUsageTips: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showRecentMaps by rememberSaveable { mutableStateOf(false) }
    var usageTipDismissedForUri by rememberSaveable { mutableStateOf<String?>(null) }
    val mapSessionKey = processingState.savedMapId
        ?: processingState.sourceUri?.toString()
        ?: processingState.rectifiedBitmap?.let { "saved-${it.hashCode()}" }
        ?: "home"
    var mapRotation by rememberSaveable(mapSessionKey) {
        mutableIntStateOf(processingState.savedRotationQuarterTurns)
    }
    val statusBarColor = MaterialTheme.colorScheme.surfaceContainer
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            statusBarColor.luminance() > 0.5f
    }
    val canNavigateHome = processingState.stage != MapProcessingStage.IDLE || processingState.error != null
    BackHandler(enabled = canNavigateHome, onBack = onReset)

    Surface(modifier = modifier) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(statusBarColor)
                    .align(Alignment.TopCenter),
            )
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when {
                processingState.isProcessing -> ProcessingScreen(onBack = onReset)
                processingState.error != null && processingState.sourceBitmap != null -> ManualBoundaryScreen(
                    bitmap = processingState.sourceBitmap,
                    error = processingState.error,
                    rotation = mapRotation,
                    onRotationChange = { mapRotation = it },
                    onApplyBoundary = onApplyBoundary,
                    onBack = onReset,
                )
                processingState.error != null -> ErrorScreen(processingState.error, onReset)
                processingState.stage == MapProcessingStage.COMPLETE -> MapFlowScreen(
                    processingState = processingState,
                    routingState = routingState,
                    defaultBudgetMeters = userPreferences.defaultDistanceBudgetKm * 1_000f,
                    showUsageTips = userPreferences.showUsageTips &&
                        usageTipDismissedForUri != processingState.sourceUri?.toString(),
                    onUsageTipDismissed = {
                        usageTipDismissedForUri = processingState.sourceUri?.toString()
                    },
                    onApplyEditedBoundary = onApplyEditedBoundary,
                    onUpdateControlPoint = onUpdateControlPoint,
                    onAddControlPoint = onAddControlPoint,
                    onRemoveControlPoint = onRemoveControlPoint,
                    onClearControlPoints = onClearControlPoints,
                    onCalculateRoute = onCalculateRoute,
                    onSaveMap = onSaveMap,
                    onRenameSavedMap = onRenameSavedMap,
                    onDeleteSavedMap = onDeleteSavedMap,
                    onReloadSavedMap = onLoadSavedMap,
                    isSavingMap = savedMapsState.isSaving,
                    rotation = mapRotation,
                    onRotationChange = { mapRotation = it },
                    onBackHome = onReset,
                )
                else -> ImageSourceScreen(
                    onImageSelected = onImageSelected,
                    onOpenSettings = { showSettings = true },
                    onOpenRecentMaps = { showRecentMaps = true },
                )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            preferences = userPreferences,
            onUpdateTheme = onUpdateTheme,
            onUpdateLanguage = onUpdateLanguage,
            onUpdateAnimations = onUpdateAnimations,
            onUpdateUsageTips = onUpdateUsageTips,
            onClearAllSavedMaps = onClearAllSavedMaps,
            hasSavedMaps = savedMapsState.maps.isNotEmpty(),
            onDismiss = { showSettings = false },
        )
    }
    if (showRecentMaps) {
        RecentMapsDialog(
            maps = savedMapsState.maps,
            onOpen = { id -> showRecentMaps = false; onLoadSavedMap(id) },
            onDismiss = { showRecentMaps = false },
        )
    }
    savedMapsState.event?.let { event ->
        MessageDialog(
            title = stringResource(
                if (event in setOf(SavedMapsEvent.SAVED, SavedMapsEvent.DELETED, SavedMapsEvent.CLEARED)) {
                    R.string.saved_maps_updated_title
                } else {
                    R.string.map_storage_error_title
                },
            ),
            message = stringResource(
                when (event) {
                    SavedMapsEvent.SAVED -> R.string.map_saved_message
                    SavedMapsEvent.DELETED -> R.string.saved_route_deleted
                    SavedMapsEvent.CLEARED -> R.string.saved_maps_cleared
                    SavedMapsEvent.SAVE_FAILED -> R.string.map_save_failed
                    SavedMapsEvent.LOAD_FAILED -> R.string.map_load_failed
                    SavedMapsEvent.RENAME_FAILED -> R.string.map_rename_failed
                    SavedMapsEvent.DELETE_FAILED -> R.string.map_delete_failed
                    SavedMapsEvent.CLEAR_FAILED -> R.string.maps_clear_failed
                },
            ),
            onDismiss = onDismissSavedMapsEvent,
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

@Composable
private fun ScreenTopBar(title: String, onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            BackButton(onBack, Modifier.align(Alignment.CenterStart))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ProcessingScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BackButton(onBack, Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.processing_map), modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ErrorScreen(error: String, onReset: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BackButton(onReset, Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.processing_failed), style = MaterialTheme.typography.titleLarge)
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = onReset) { Text(stringResource(R.string.start_over)) }
        }
    }
}

@Composable
private fun ManualBoundaryScreen(
    bitmap: Bitmap,
    error: String,
    rotation: Int,
    onRotationChange: (Int) -> Unit,
    onApplyBoundary: (MapBoundary) -> Unit,
    onBack: () -> Unit,
) {
    var boundary by remember(bitmap) { mutableStateOf(initialBoundary(bitmap)) }
    var showDetectionError by rememberSaveable(error) { mutableStateOf(true) }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.select_map_boundary), onBack)
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            InteractiveCornerCanvas(
                bitmap = bitmap,
                boundary = boundary,
                onBoundaryChange = { boundary = it },
                rotationQuarterTurns = rotation,
                recenterKey = recenterKey,
                modifier = Modifier.fillMaxSize(),
            )
            MapOverlayButtons(
                onRotate = { onRotationChange(rotation + 1) },
                onRecenter = { recenterKey++ },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
        Button(
            onClick = { onApplyBoundary(boundary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text(stringResource(R.string.apply_boundary)) }
    }

    if (showDetectionError) {
        MessageDialog(
            title = stringResource(R.string.boundary_not_detected_title),
            message = "$error\n\n${stringResource(R.string.boundary_help)}",
            onDismiss = { showDetectionError = false },
        )
    }
}

internal fun initialBoundary(bitmap: Bitmap): MapBoundary {
    val insetX = bitmap.width * 0.05f
    val insetY = bitmap.height * 0.05f
    return MapBoundary(
        topLeft = Point2D(insetX, insetY),
        topRight = Point2D(bitmap.width - insetX, insetY),
        bottomRight = Point2D(bitmap.width - insetX, bitmap.height - insetY),
        bottomLeft = Point2D(insetX, bitmap.height - insetY),
    )
}

private enum class MapPage { EDIT, ROUTE }
private enum class EditMode { CALIBRATION, CORNERS, POINTS }

@Composable
private fun MapFlowScreen(
    processingState: MapProcessingUiState,
    routingState: RoutingUiState,
    defaultBudgetMeters: Float,
    showUsageTips: Boolean,
    onUsageTipDismissed: () -> Unit,
    onApplyEditedBoundary: (MapBoundary, Point2D?, Point2D?, Float?) -> Unit,
    onUpdateControlPoint: (ControlPoint) -> Unit,
    onAddControlPoint: (ControlPoint) -> Unit,
    onRemoveControlPoint: (String) -> Unit,
    onClearControlPoints: () -> Unit,
    onCalculateRoute: (Float, RouteMode, Float?, Int?) -> Unit,
    onSaveMap: (SavedMapDraft, (ScannedMapEntity) -> Unit) -> Unit,
    onRenameSavedMap: (String, String, (ScannedMapEntity) -> Unit) -> Unit,
    onDeleteSavedMap: (String, () -> Unit) -> Unit,
    onReloadSavedMap: (String) -> Unit,
    isSavingMap: Boolean,
    rotation: Int,
    onRotationChange: (Int) -> Unit,
    onBackHome: () -> Unit,
) {
    val sessionKey = processingState.savedMapId ?: processingState.sourceUri?.toString()
        ?: processingState.rectifiedBitmap?.hashCode()?.toString()
    var page by rememberSaveable(sessionKey) {
        mutableStateOf(
            if (processingState.savedMapId != null && routingState.route != null) MapPage.ROUTE else MapPage.EDIT,
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
    var isDirty by rememberSaveable(sessionKey) {
        mutableStateOf(processingState.savedMapId == null || processingState.savedContentDirty)
    }
    var editSessionKey by rememberSaveable(sessionKey) { mutableIntStateOf(0) }
    val linePixels = if (lineStart != null && lineEnd != null) {
        hypot(lineEnd!!.x - lineStart!!.x, lineEnd!!.y - lineStart!!.y)
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
    val visiblePage = if (page == MapPage.ROUTE && routingState.route != null) MapPage.ROUTE else MapPage.EDIT
    val animationsEnabled = LocalAnimationsEnabled.current

    Crossfade(
        targetState = visiblePage,
        animationSpec = tween(if (animationsEnabled) 180 else 0),
        label = "mapPage",
    ) { targetPage ->
    if (targetPage == MapPage.ROUTE && routingState.route != null) {
        RouteScreen(
            bitmap = requireNotNull(processingState.rectifiedBitmap),
            route = routingState.route,
            alternativeRoutes = routingState.alternativeRoutes,
            nextLongestRouteCount = routingState.nextLongestRouteCount,
            preferredSelectedRoutePointIds = routingState.selectedRoutePointIds,
            allPoints = processingState.controlPoints,
            rotation = rotation,
            onRotationChange = { isDirty = true; onRotationChange(it) },
            canSave = effectivePixelsPerMeter != null,
            isSaving = isSavingMap,
            savedMapId = savedMapId,
            savedMapName = savedMapName,
            isDirty = isDirty,
            onSave = { selectedRoute ->
                effectivePixelsPerMeter?.let { scale ->
                    onSaveMap(
                        SavedMapDraft(
                            bitmap = rectified,
                            pixelsPerMeter = scale,
                            points = processingState.controlPoints,
                            route = routingState.route,
                            selectedRoute = selectedRoute,
                            routeMode = routingState.mode.name,
                            routeBudgetMeters = routingState.budgetMeters,
                            routeTargetScore = routingState.targetScore,
                            lineStart = lineStart,
                            lineEnd = lineEnd,
                            lineDistanceMeters = lineMeters,
                            rotationQuarterTurns = rotation,
                            existingId = savedMapId,
                            name = savedMapName,
                        ),
                    ) { saved ->
                        savedMapId = saved.id
                        savedMapName = saved.name
                        isDirty = false
                    }
                }
            },
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
                editSessionKey++
                page = MapPage.EDIT
            },
            onHome = onBackHome,
        )
    } else {
        EditMapScreen(
            processingState = processingState,
            routingState = routingState,
            editSessionKey = editSessionKey,
            defaultBudgetMeters = defaultBudgetMeters,
            showUsageTips = showUsageTips,
            onUsageTipDismissed = onUsageTipDismissed,
            onApplyBoundary = {
                isDirty = true
                onApplyEditedBoundary(it, lineStart, lineEnd, lineMeters)
            },
            onUpdateControlPoint = { isDirty = true; onUpdateControlPoint(it) },
            onAddControlPoint = { isDirty = true; onAddControlPoint(it) },
            onRemoveControlPoint = { isDirty = true; onRemoveControlPoint(it) },
            onClearControlPoints = { isDirty = true; onClearControlPoints() },
            onCalculateRoute = { scale, mode, budget, targetScore ->
                isDirty = true
                onCalculateRoute(scale, mode, budget, targetScore)
            },
            lineStart = lineStart,
            lineEnd = lineEnd,
            lineDistanceText = lineDistanceText,
            onLineChange = { start, end -> isDirty = true; lineStart = start; lineEnd = end },
            onLineDistanceChange = { isDirty = true; lineDistanceText = it },
            rotation = rotation,
            onRotationChange = { isDirty = true; onRotationChange(it) },
            onDiscardChanges = savedMapId?.let { id ->
                { page = MapPage.ROUTE; onReloadSavedMap(id) }
            },
            onBack = savedMapId?.let { id ->
                { page = MapPage.ROUTE; onReloadSavedMap(id) }
            } ?: onBackHome,
        )
    }
    }
}

@Composable
private fun EditMapScreen(
    processingState: MapProcessingUiState,
    routingState: RoutingUiState,
    editSessionKey: Int,
    defaultBudgetMeters: Float,
    showUsageTips: Boolean,
    onUsageTipDismissed: () -> Unit,
    onApplyBoundary: (MapBoundary) -> Unit,
    onUpdateControlPoint: (ControlPoint) -> Unit,
    onAddControlPoint: (ControlPoint) -> Unit,
    onRemoveControlPoint: (String) -> Unit,
    onClearControlPoints: () -> Unit,
    onCalculateRoute: (Float, RouteMode, Float?, Int?) -> Unit,
    lineStart: Point2D?,
    lineEnd: Point2D?,
    lineDistanceText: String,
    onLineChange: (Point2D?, Point2D?) -> Unit,
    onLineDistanceChange: (String) -> Unit,
    rotation: Int,
    onRotationChange: (Int) -> Unit,
    onDiscardChanges: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val rectified = requireNotNull(processingState.rectifiedBitmap)
    var editMode by rememberSaveable { mutableStateOf(EditMode.CALIBRATION) }
    var editedBoundary by remember(processingState.boundary) { mutableStateOf(processingState.boundary) }
    var budgetText by rememberSaveable(editSessionKey, defaultBudgetMeters, routingState.budgetMeters) {
        mutableStateOf((routingState.budgetMeters ?: defaultBudgetMeters).toString())
    }
    var targetScoreText by rememberSaveable(editSessionKey, routingState.targetScore) {
        mutableStateOf((routingState.targetScore ?: 10).toString())
    }
    var mode by rememberSaveable(editSessionKey, routingState.mode) {
        mutableStateOf(routingState.mode)
    }
    var dismissedRoutingError by remember { mutableStateOf<String?>(null) }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }
    var showHelp by rememberSaveable(processingState.sourceUri?.toString()) { mutableStateOf(showUsageTips) }
    var selectedPointId by rememberSaveable { mutableStateOf<String?>(null) }
    var newPointId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLineReset by rememberSaveable { mutableStateOf(false) }
    var confirmPointsClear by rememberSaveable { mutableStateOf(false) }
    var confirmLeaveEdit by rememberSaveable { mutableStateOf(false) }
    var viewportCenterPoint by remember(rectified) {
        mutableStateOf(Point2D(rectified.width / 2f, rectified.height / 2f))
    }
    val linePixels = if (lineStart != null && lineEnd != null) {
        hypot(lineEnd!!.x - lineStart!!.x, lineEnd!!.y - lineStart!!.y)
    } else {
        0f
    }
    val lineMeters = lineDistanceText.localizedFloatOrNull()
    val effectivePixelsPerMeter = if (linePixels > 0f && lineMeters != null && lineMeters > 0f) {
        linePixels / lineMeters
    } else {
        processingState.savedPixelsPerMeter
    }
    val routingParametersValid = when (mode) {
        RouteMode.SHORTEST -> true
        RouteMode.BEST_SCORE -> budgetText.localizedFloatOrNull()?.let { it > 0f } == true
        RouteMode.TARGET_SCORE -> targetScoreText.toIntOrNull()?.let { it > 0 } == true
    }

    val leaveSubEditor = { editMode = EditMode.CALIBRATION }
    val requestBack = {
        if (editMode != EditMode.CALIBRATION) leaveSubEditor() else confirmLeaveEdit = true
    }
    BackHandler(onBack = requestBack)

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = stringResource(
                when (editMode) {
                    EditMode.CALIBRATION -> R.string.edit_map
                    EditMode.CORNERS -> R.string.edit_corners
                    EditMode.POINTS -> R.string.edit_points
                },
            ),
            onBack = requestBack,
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                when (editMode) {
                    EditMode.CORNERS -> if (processingState.sourceBitmap != null && editedBoundary != null) {
                        InteractiveCornerCanvas(
                            bitmap = processingState.sourceBitmap,
                            boundary = editedBoundary!!,
                            onBoundaryChange = { editedBoundary = it },
                            rotationQuarterTurns = rotation,
                            recenterKey = recenterKey,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    EditMode.POINTS -> InteractiveControlPointCanvas(
                        bitmap = rectified,
                        points = processingState.controlPoints,
                        rotationQuarterTurns = rotation,
                        onPointMoved = onUpdateControlPoint,
                        onPointSelected = { selectedPointId = it.id },
                        onViewportCenterChange = { viewportCenterPoint = it },
                        recenterKey = recenterKey,
                        modifier = Modifier.fillMaxSize(),
                    )
                    EditMode.CALIBRATION -> DistanceCalibrationCanvas(
                        bitmap = rectified,
                        start = lineStart,
                        end = lineEnd,
                        onLineChange = onLineChange,
                        controlPoints = processingState.controlPoints,
                        rotationQuarterTurns = rotation,
                        recenterKey = recenterKey,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                MapOverlayButtons(
                    onRotate = { onRotationChange(rotation + 1) },
                    onRecenter = { recenterKey++ },
                    onResetLine = if (editMode == EditMode.CALIBRATION) {
                        { confirmLineReset = true }
                    } else {
                        null
                    },
                    onClearPoints = if (editMode == EditMode.POINTS && processingState.controlPoints.isNotEmpty()) {
                        { confirmPointsClear = true }
                    } else {
                        null
                    },
                    onAddPoint = if (editMode == EditMode.POINTS) {
                        {
                            val point = ControlPoint(
                                code = 0,
                                center = viewportCenterPoint,
                            )
                            onAddControlPoint(point)
                            selectedPointId = point.id
                            newPointId = point.id
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }

            when (editMode) {
                EditMode.CORNERS -> Button(
                    onClick = {
                        editedBoundary?.let(onApplyBoundary)
                        editMode = EditMode.CALIBRATION
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.apply_corners)) }

                EditMode.POINTS -> Button(
                    onClick = { editMode = EditMode.CALIBRATION },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(stringResource(R.string.done_editing_points)) }

                EditMode.CALIBRATION -> {
                    OutlinedTextField(
                        value = lineDistanceText,
                        onValueChange = onLineDistanceChange,
                        enabled = lineEnd != null,
                        label = { Text(stringResource(R.string.line_distance)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { editMode = EditMode.CORNERS },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null)
                            Text(stringResource(R.string.edit_corners), modifier = Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { editMode = EditMode.POINTS },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(stringResource(R.string.edit_points), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = mode == RouteMode.SHORTEST,
                                onClick = { mode = RouteMode.SHORTEST },
                                label = {
                                    Text(stringResource(R.string.route_mode_shortest), maxLines = 1)
                                },
                            )
                        }
                        item {
                            FilterChip(
                                selected = mode == RouteMode.BEST_SCORE,
                                onClick = { mode = RouteMode.BEST_SCORE },
                                label = {
                                    Text(stringResource(R.string.route_mode_score), maxLines = 1)
                                },
                            )
                        }
                        item {
                            FilterChip(
                                selected = mode == RouteMode.TARGET_SCORE,
                                onClick = { mode = RouteMode.TARGET_SCORE },
                                label = {
                                    Text(stringResource(R.string.route_mode_target_score), maxLines = 1)
                                },
                            )
                        }
                    }
                    if (mode == RouteMode.BEST_SCORE) {
                        OutlinedTextField(
                            value = budgetText,
                            onValueChange = { budgetText = it },
                            label = { Text(stringResource(R.string.distance_budget)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                    if (mode == RouteMode.TARGET_SCORE) {
                        OutlinedTextField(
                            value = targetScoreText,
                            onValueChange = { targetScoreText = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.target_score)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        onDiscardChanges?.let { discard ->
                            OutlinedButton(onClick = discard, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.discard_changes), textAlign = TextAlign.Center)
                            }
                        }
                        Button(
                            enabled = !routingState.isCalculating &&
                                effectivePixelsPerMeter != null && routingParametersValid,
                            onClick = {
                                dismissedRoutingError = null
                                onCalculateRoute(
                                    requireNotNull(effectivePixelsPerMeter),
                                    mode,
                                    if (mode == RouteMode.BEST_SCORE) budgetText.localizedFloatOrNull() else null,
                                    if (mode == RouteMode.TARGET_SCORE) targetScoreText.toIntOrNull() else null,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (routingState.isCalculating) CircularProgressIndicator(Modifier.height(20.dp))
                            Text(
                                stringResource(
                                    if (routingState.isCalculating) {
                                        R.string.calculating_route
                                    } else {
                                        R.string.calculate_route
                                    },
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(start = if (routingState.isCalculating) 8.dp else 0.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHelp) {
        MessageDialog(
            title = stringResource(R.string.usage_tip_title),
            message = stringResource(
                when (editMode) {
                    EditMode.CALIBRATION -> R.string.calibration_help
                    EditMode.CORNERS -> R.string.boundary_help
                    EditMode.POINTS -> R.string.point_edit_help
                },
            ),
            onDismiss = {
                showHelp = false
                onUsageTipDismissed()
            },
        )
    }

    if (confirmLeaveEdit) {
        ConfirmationDialog(
            title = stringResource(R.string.leave_map_edit_title),
            message = stringResource(R.string.leave_map_edit_confirmation),
            onConfirm = { confirmLeaveEdit = false; onBack() },
            onDismiss = { confirmLeaveEdit = false },
        )
    }

    selectedPointId?.let { id ->
        processingState.controlPoints.firstOrNull { it.id == id }?.let { point ->
            ControlPointEditorDialog(
                point = point,
                isNew = point.id == newPointId,
                onSave = {
                    onUpdateControlPoint(it)
                    newPointId = null
                    selectedPointId = null
                },
                onDelete = { onRemoveControlPoint(point.id); selectedPointId = null },
                onDismiss = {
                    if (point.id == newPointId) {
                        onRemoveControlPoint(point.id)
                        newPointId = null
                    }
                    selectedPointId = null
                },
            )
        }
    }

    routingState.error?.takeUnless { it == dismissedRoutingError }?.let { error ->
        MessageDialog(
            title = stringResource(R.string.route_error_title),
            message = error,
            onDismiss = { dismissedRoutingError = error },
        )
    }
    if (confirmLineReset) {
        ConfirmationDialog(
            title = stringResource(R.string.clear_line_title),
            message = stringResource(R.string.clear_line_confirmation),
            onConfirm = {
                onLineChange(null, null)
                onLineDistanceChange("")
                confirmLineReset = false
            },
            onDismiss = { confirmLineReset = false },
        )
    }
    if (confirmPointsClear) {
        ConfirmationDialog(
            title = stringResource(R.string.clear_points_title),
            message = stringResource(R.string.clear_points_confirmation),
            onConfirm = {
                onClearControlPoints()
                confirmPointsClear = false
            },
            onDismiss = { confirmPointsClear = false },
        )
    }
}

@Composable
private fun MapOverlayButtons(
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
    onRecenter: (() -> Unit)? = null,
    onResetLine: (() -> Unit)? = null,
    onAddPoint: (() -> Unit)? = null,
    onClearPoints: (() -> Unit)? = null,
    onEditMap: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 5.dp,
    ) {
        Column(Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            onRecenter?.let { recenter ->
                IconButton(onClick = recenter, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = stringResource(R.string.recenter_view),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onRotate, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = stringResource(R.string.rotate_map),
                    modifier = Modifier.size(20.dp),
                )
            }
            onEditMap?.let { edit ->
                IconButton(onClick = edit, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_map),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            onResetLine?.let { reset ->
                IconButton(onClick = reset, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.reset_line),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            onClearPoints?.let { clear ->
                IconButton(onClick = clear, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = stringResource(R.string.clear_points),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            onAddPoint?.let { add ->
                IconButton(onClick = add, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.AddLocationAlt,
                        contentDescription = stringResource(R.string.add_point),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPointEditorDialog(
    point: ControlPoint,
    isNew: Boolean,
    onSave: (ControlPoint) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var codeText by rememberSaveable(point.id) {
        mutableStateOf(if (isNew) "" else point.code.toString())
    }
    var type by rememberSaveable(point.id) { mutableStateOf(point.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            CenteredDialogTitle(
                stringResource(if (isNew) R.string.add_point else R.string.edit_point),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type == ControlPointType.CONTROL) {
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.control_code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ControlPointType.entries.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { option ->
                                FilterChip(
                                    selected = type == option,
                                    onClick = { type = option },
                                    label = { Text(controlTypeLabel(option)) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(stringResource(R.string.delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    onClick = {
                        val code = if (type == ControlPointType.CONTROL) codeText.toIntOrNull() ?: 0 else 0
                        onSave(point.copy(code = code, points = if (type == ControlPointType.CONTROL) code / 10 else 0, type = type))
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        },
    )
}

@Composable
private fun controlTypeLabel(type: ControlPointType): String = stringResource(
    when (type) {
        ControlPointType.START -> R.string.point_type_start
        ControlPointType.FINISH -> R.string.point_type_finish
        ControlPointType.START_FINISH -> R.string.point_type_start_finish
        ControlPointType.CONTROL -> R.string.point_type_control
    },
)

@Composable
private fun RouteScreen(
    bitmap: Bitmap,
    route: OptimizedRoute,
    alternativeRoutes: List<OptimizedRoute>,
    nextLongestRouteCount: Int,
    preferredSelectedRoutePointIds: List<String>,
    allPoints: List<ControlPoint>,
    rotation: Int,
    onRotationChange: (Int) -> Unit,
    canSave: Boolean,
    isSaving: Boolean,
    savedMapId: String?,
    savedMapName: String?,
    isDirty: Boolean,
    onSave: (OptimizedRoute) -> Unit,
    onRouteSelectionChanged: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onHome: () -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var renameText by rememberSaveable(savedMapId, savedMapName) { mutableStateOf(savedMapName.orEmpty()) }
    val higherScoreRoutes = alternativeRoutes.take(nextLongestRouteCount)
    val lowerScoreRoutes = alternativeRoutes.drop(nextLongestRouteCount)
    val availableRoutes = higherScoreRoutes + route + lowerScoreRoutes
    val primaryRouteIndex = higherScoreRoutes.size
    val primaryRoutePathKey = route.path.joinToString("|") { it.id }
    val preferredRoutePathKey = preferredSelectedRoutePointIds
        .takeIf(List<String>::isNotEmpty)
        ?.joinToString("|")
        ?: primaryRoutePathKey
    val routeSelectionKey = buildString {
        append(route.path.joinToString(":") { it.id })
        append('|').append(route.totalScore)
        append('|').append(route.totalDistanceMeters)
    }
    var selectedRoutePathKey by rememberSaveable(routeSelectionKey) {
        mutableStateOf(preferredRoutePathKey)
    }
    val safeSelectedRouteIndex = availableRoutes.indexOfFirst { candidate ->
        candidate.path.joinToString("|") { it.id } == selectedRoutePathKey
    }.takeIf { it >= 0 } ?: primaryRouteIndex
    val displayedRoute = availableRoutes[safeSelectedRouteIndex]

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                IconButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home))
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 184.dp),
                    )
                    if (savedMapId != null) {
                        IconButton(onClick = { showRename = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.DriveFileRenameOutline,
                                contentDescription = stringResource(R.string.rename_route),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Row(Modifier.align(Alignment.CenterEnd)) {
                    if (isDirty || savedMapId == null) {
                        IconButton(
                            onClick = { onSave(displayedRoute) },
                            enabled = canSave && !isSaving,
                        ) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save_map))
                        }
                    }
                    if (savedMapId != null && !isDirty) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_saved_route))
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            RouteRenderingCanvas(
                bitmap = bitmap,
                route = displayedRoute.path,
                allPoints = allPoints,
                rotationQuarterTurns = rotation,
                recenterKey = recenterKey,
                modifier = Modifier.fillMaxSize(),
            )
            MapOverlayButtons(
                onRotate = { onRotationChange(rotation + 1) },
                onRecenter = { recenterKey++ },
                onEditMap = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(
                    R.string.route_summary,
                    displayedRoute.totalDistanceMeters,
                    displayedRoute.totalScore,
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = null)
                }
                OutlinedButton(onClick = { showDetails = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.show_route_details))
                }
            }
        }
    }

    if (showDetails) {
        RouteDetailsBottomSheet(
            route = route,
            alternativeRoutes = alternativeRoutes,
            nextLongestRouteCount = nextLongestRouteCount,
            selectedRouteIndex = safeSelectedRouteIndex,
            onRouteSelected = { index ->
                val newPathKey = availableRoutes[index].path.joinToString("|") { it.id }
                if (newPathKey != selectedRoutePathKey) {
                    selectedRoutePathKey = newPathKey
                    onRouteSelectionChanged()
                }
            },
            onDismissRequest = { showDetails = false },
        )
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
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
}

@Composable
private fun RecentMapsDialog(
    maps: List<ScannedMapEntity>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(stringResource(R.string.recent_maps)) },
        text = {
            if (maps.isEmpty()) {
                Text(stringResource(R.string.no_recent_maps))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    maps.take(8).forEach { map ->
                        OutlinedButton(
                            onClick = { onOpen(map.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${map.name}\n${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(map.timestamp))}",
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
    )
}

@Composable
private fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
    )
}

@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CenteredDialogTitle(title: String) {
    Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SettingsDialog(
    preferences: UserPreferences,
    onUpdateTheme: (ThemeConfig) -> Unit,
    onUpdateLanguage: (LanguageConfig) -> Unit,
    onUpdateAnimations: (Boolean) -> Unit,
    onUpdateUsageTips: (Boolean) -> Unit,
    onClearAllSavedMaps: () -> Unit,
    hasSavedMaps: Boolean,
    onDismiss: () -> Unit,
) {
    var confirmClearSavedMaps by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsChipGroup(
                    label = stringResource(R.string.theme),
                    options = ThemeConfig.entries,
                    selected = preferences.themeConfig,
                    optionLabel = { themeLabel(it) },
                    onSelect = onUpdateTheme,
                )
                HorizontalDivider()
                SettingsChipGroup(
                    label = stringResource(R.string.language),
                    options = LanguageConfig.entries,
                    selected = preferences.language,
                    optionLabel = { languageLabel(it) },
                    onSelect = onUpdateLanguage,
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.animations), style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = preferences.useAnimations,
                        onCheckedChange = onUpdateAnimations,
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.usage_tips), style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = preferences.showUsageTips,
                        onCheckedChange = onUpdateUsageTips,
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
                    )
                }
                HorizontalDivider()
                TextButton(
                    onClick = { confirmClearSavedMaps = true },
                    enabled = hasSavedMaps,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Text(stringResource(R.string.clear_saved_maps))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    if (confirmClearSavedMaps) {
        ConfirmationDialog(
            title = stringResource(R.string.clear_saved_maps_title),
            message = stringResource(R.string.clear_saved_maps_confirmation),
            onConfirm = { onClearAllSavedMaps(); confirmClearSavedMaps = false },
            onDismiss = { confirmClearSavedMaps = false },
        )
    }
}

@Composable
private fun <T> SettingsChipGroup(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Composable
private fun themeLabel(theme: ThemeConfig): String = stringResource(
    when (theme) {
        ThemeConfig.SYSTEM -> R.string.theme_system
        ThemeConfig.LIGHT -> R.string.theme_light
        ThemeConfig.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageLabel(language: LanguageConfig): String = stringResource(
    when (language) {
        LanguageConfig.SYSTEM -> R.string.language_system
        LanguageConfig.ENGLISH -> R.string.language_english
        LanguageConfig.LATVIAN -> R.string.language_latvian
    },
)

private fun String.localizedFloatOrNull(): Float? = replace(',', '.').toFloatOrNull()
