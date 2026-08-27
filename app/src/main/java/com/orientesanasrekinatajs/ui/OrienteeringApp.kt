package com.orientesanasrekinatajs.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Map as MapIcon
import androidx.compose.material.icons.outlined.Map as OutlinedMapIcon
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.data.transfer.MapTransferRepository
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.domain.model.RouteRestrictionType
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import com.orientesanasrekinatajs.domain.model.UserPreferences
import com.orientesanasrekinatajs.ui.components.DistanceCalibrationCanvas
import com.orientesanasrekinatajs.ui.components.ImageSourceScreen
import com.orientesanasrekinatajs.ui.components.InteractiveCornerCanvas
import com.orientesanasrekinatajs.ui.components.InteractiveControlPointCanvas
import com.orientesanasrekinatajs.ui.components.RouteDetailsBottomSheet
import com.orientesanasrekinatajs.ui.components.RouteEditorBottomSheet
import com.orientesanasrekinatajs.ui.components.RouteRenderingCanvas
import com.orientesanasrekinatajs.ui.components.RouteRenderLayer
import com.orientesanasrekinatajs.ui.components.routeColor
import com.orientesanasrekinatajs.ui.processing.MapProcessingStage
import com.orientesanasrekinatajs.ui.processing.MapProcessingUiState
import com.orientesanasrekinatajs.ui.routing.RouteMode
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction
import com.orientesanasrekinatajs.ui.routing.RouteRestrictionAction
import com.orientesanasrekinatajs.ui.routing.AlternativeRouteCriteria
import com.orientesanasrekinatajs.ui.routing.RoutingUiState
import com.orientesanasrekinatajs.ui.savedmaps.SavedMapsEvent
import com.orientesanasrekinatajs.ui.savedmaps.SavedMapsUiState
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import com.orientesanasrekinatajs.ui.transfer.MapTransferEvent
import com.orientesanasrekinatajs.ui.transfer.MapTransferUiState
import java.text.DateFormat
import java.util.Date
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun OrienteeringApp(
    processingState: MapProcessingUiState,
    routingState: RoutingUiState,
    userPreferences: UserPreferences,
    savedMapsState: SavedMapsUiState,
    mapTransferState: MapTransferUiState,
    onImageSelected: (Uri) -> Unit,
    onApplyBoundary: (MapBoundary) -> Unit,
    onApplyEditedBoundary: (MapBoundary, Point2D?, Point2D?, Float?) -> Unit,
    onUpdateControlPoint: (ControlPoint) -> Unit,
    onAddControlPoint: (ControlPoint) -> Unit,
    onRemoveControlPoint: (String) -> Unit,
    onClearControlPoints: () -> Unit,
    onInvalidateRoute: () -> Unit,
    onCalculateRoute: (
        pixelsPerMeter: Float,
        mode: RouteMode,
        budgetMeters: Float?,
        targetScore: Int?,
    ) -> Unit,
    onGenerateAlternativeRoutes: (List<ControlPoint>, Float, AlternativeRouteCriteria) -> Unit,
    onManageRoutes: (RouteManagementAction) -> Unit,
    onManageRouteRestrictions: (RouteRestrictionAction) -> Unit,
    onExportMap: (Uri, SavedMapDraft, Boolean) -> Unit,
    onImportMap: (Uri) -> Unit,
    onSaveMap: (SavedMapDraft, (ScannedMapEntity) -> Unit) -> Unit,
    onLoadSavedMap: (String) -> Unit,
    onRenameSavedMap: (String, String, (ScannedMapEntity) -> Unit) -> Unit,
    onDeleteSavedMap: (String, () -> Unit) -> Unit,
    onClearAllSavedMaps: () -> Unit,
    onDismissSavedMapsEvent: () -> Unit,
    onDismissMapTransferEvent: () -> Unit,
    onReset: () -> Unit,
    onUpdateTheme: (ThemeConfig) -> Unit,
    onUpdateLanguage: (LanguageConfig) -> Unit,
    onUpdateAnimations: (Boolean) -> Unit,
    onUpdateMapRotationGestures: (Boolean) -> Unit,
    onUpdatePointsPerKilometer: (Boolean) -> Unit,
    onUpdateUsageTips: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showRecentMaps by rememberSaveable { mutableStateOf(false) }
    var usageTipDismissedForUri by rememberSaveable { mutableStateOf<String?>(null) }
    val mapSessionKey = processingState.savedMapId
        ?: processingState.sourceUri?.toString()
        ?: processingState.rectifiedBitmap?.let { "saved-${it.hashCode()}" }
        ?: "home"
    val rotationSessionKey = processingState.rectifiedBitmap?.let { "bitmap-${it.hashCode()}" }
        ?: processingState.sourceUri?.toString()
        ?: mapSessionKey
    var mapRotation by rememberSaveable(rotationSessionKey) {
        mutableIntStateOf(processingState.savedRotationQuarterTurns)
    }
    var mapRotationOffsetDegrees by rememberSaveable(rotationSessionKey) { mutableFloatStateOf(0f) }
    val applyRotationGesture: (Float) -> Unit = { change ->
        if (userPreferences.disableMapRotationGestures && change.isFinite()) {
            mapRotationOffsetDegrees = (mapRotationOffsetDegrees + change) % 360f
        }
    }
    val snapMapRotation: () -> Unit = {
        mapRotation = ((mapRotation * 90f + mapRotationOffsetDegrees) / 90f).roundToInt()
        mapRotationOffsetDegrees = 0f
    }
    val setMapRotation: (Int) -> Unit = { quarterTurns ->
        mapRotation = quarterTurns
        mapRotationOffsetDegrees = 0f
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

    Surface(modifier = modifier.clearFocusOnPointerDown(focusManager)) {
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
                processingState.error != null && processingState.manualBoundaryRequired &&
                    processingState.sourceBitmap != null -> ManualBoundaryScreen(
                    bitmap = processingState.sourceBitmap,
                    error = processingState.error,
                    rotation = mapRotation,
                    rotationOffsetDegrees = mapRotationOffsetDegrees,
                    rotationGesturesEnabled = userPreferences.disableMapRotationGestures,
                    onRotationGesture = applyRotationGesture,
                    onSnapRotation = snapMapRotation,
                    onRotationChange = setMapRotation,
                    onApplyBoundary = onApplyBoundary,
                    onBack = onReset,
                )
                processingState.error != null -> ErrorScreen(processingState.error, onReset)
                processingState.stage == MapProcessingStage.COMPLETE -> MapFlowScreen(
                    processingState = processingState,
                    routingState = routingState,
                    defaultBudgetMeters = userPreferences.defaultDistanceBudgetKm * 1_000f,
                    showPointsPerKilometer = userPreferences.showPointsPerKilometer,
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
                    onInvalidateRoute = onInvalidateRoute,
                    onCalculateRoute = onCalculateRoute,
                    onGenerateAlternativeRoutes = onGenerateAlternativeRoutes,
                    onManageRoutes = onManageRoutes,
                    onManageRouteRestrictions = onManageRouteRestrictions,
                    onExportMap = onExportMap,
                    onSaveMap = onSaveMap,
                    onRenameSavedMap = onRenameSavedMap,
                    onDeleteSavedMap = onDeleteSavedMap,
                    onReloadSavedMap = onLoadSavedMap,
                    isSavingMap = savedMapsState.isSaving,
                    isTransferringMap = mapTransferState.isWorking,
                    rotation = mapRotation,
                    rotationOffsetDegrees = mapRotationOffsetDegrees,
                    rotationGesturesEnabled = userPreferences.disableMapRotationGestures,
                    onRotationGesture = applyRotationGesture,
                    onSnapRotation = snapMapRotation,
                    onRotationChange = setMapRotation,
                    onBackHome = onReset,
                )
                else -> ImageSourceScreen(
                    onImageSelected = onImageSelected,
                    onOpenSettings = { showSettings = true },
                    onOpenRecentMaps = { showRecentMaps = true },
                    onImportMap = onImportMap,
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
            onUpdateMapRotationGestures = onUpdateMapRotationGestures,
            onUpdatePointsPerKilometer = onUpdatePointsPerKilometer,
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
    mapTransferState.event?.let { event ->
        MessageDialog(
            title = stringResource(
                if (event == MapTransferEvent.EXPORTED) R.string.map_exported_title
                else R.string.map_transfer_error_title,
            ),
            message = stringResource(
                when (event) {
                    MapTransferEvent.EXPORTED -> R.string.map_exported_message
                    MapTransferEvent.EXPORT_FAILED -> R.string.map_export_failed
                    MapTransferEvent.IMPORT_FAILED -> R.string.map_import_failed
                },
            ),
            onDismiss = onDismissMapTransferEvent,
        )
    }
}

private fun Modifier.clearFocusOnPointerDown(focusManager: FocusManager): Modifier = pointerInput(focusManager) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        focusManager.clearFocus(force = true)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

@Composable
private fun ScreenTopBar(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            BackButton(onBack, Modifier.align(Alignment.CenterStart))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            action?.let { content ->
                Box(Modifier.align(Alignment.CenterEnd)) { content() }
            }
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
    rotationOffsetDegrees: Float,
    rotationGesturesEnabled: Boolean,
    onRotationGesture: (Float) -> Unit,
    onSnapRotation: () -> Unit,
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
                rotationOffsetDegrees = rotationOffsetDegrees,
                rotationGesturesEnabled = rotationGesturesEnabled,
                onRotationGesture = onRotationGesture,
                recenterKey = recenterKey,
                modifier = Modifier.fillMaxSize(),
            )
            MapOverlayButtons(
                onRotate = { onRotationChange(rotation + 1) },
                onRecenter = { onSnapRotation(); recenterKey++ },
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
                rotationQuarterTurns = ((rotation * 90f + rotationOffsetDegrees) / 90f).roundToInt(),
                existingId = existingId,
                name = name,
            )
        }
    }
    var showExportOptions by rememberSaveable { mutableStateOf(false) }
    var pendingExportIncludesRoutes by rememberSaveable { mutableStateOf(false) }
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
            onRotationChange = { isDirty = true; onRotationChange(it) },
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
            onRotationChange = {
                isDirty = true
                mapEditChangedSinceEntry = true
                onInvalidateRoute()
                onRotationChange(it)
            },
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
            onExport = { includeRoutes ->
                pendingExportIncludesRoutes = includeRoutes
                showExportOptions = false
                val baseName = (savedMapName?.takeIf(String::isNotBlank) ?: defaultRouteName)
                    .replace(Regex("[^A-Za-z0-9._ -]"), "_")
                    .trim()
                    .ifBlank { "map" }
                exportLauncher.launch("$baseName.${MapTransferRepository.FILE_EXTENSION}")
            },
            onDismiss = { showExportOptions = false },
        )
    }
}

@Composable
private fun ExportMapDialog(
    hasRoutes: Boolean,
    isExporting: Boolean,
    onExport: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeRoutes by rememberSaveable(hasRoutes) { mutableStateOf(hasRoutes) }
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
                    Text(stringResource(R.string.include_all_routes), modifier = Modifier.weight(1f))
                    Switch(
                        checked = includeRoutes,
                        onCheckedChange = { includeRoutes = it },
                        enabled = hasRoutes && !isExporting,
                    )
                }
                Text(
                    stringResource(
                        if (includeRoutes && hasRoutes) R.string.export_map_and_routes_description
                        else R.string.export_map_only_description,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(includeRoutes && hasRoutes) }, enabled = !isExporting) {
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

@Composable
private fun EditMapScreen(
    processingState: MapProcessingUiState,
    showUsageTips: Boolean,
    onUsageTipDismissed: () -> Unit,
    onApplyBoundary: (MapBoundary) -> Unit,
    onUpdateControlPoint: (ControlPoint) -> Unit,
    onAddControlPoint: (ControlPoint) -> Unit,
    onRemoveControlPoint: (String) -> Unit,
    onClearControlPoints: () -> Unit,
    onOpenRoute: () -> Unit,
    isSaving: Boolean,
    isDirty: Boolean,
    lineStart: Point2D?,
    lineEnd: Point2D?,
    lineDistanceText: String,
    onLineChange: (Point2D?, Point2D?) -> Unit,
    onLineDistanceChange: (String) -> Unit,
    rotation: Int,
    rotationOffsetDegrees: Float,
    rotationGesturesEnabled: Boolean,
    onRotationGesture: (Float) -> Unit,
    onSnapRotation: () -> Unit,
    onRotationChange: (Int) -> Unit,
    onCancel: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val rectified = requireNotNull(processingState.rectifiedBitmap)
    var editMode by rememberSaveable { mutableStateOf(EditMode.CALIBRATION) }
    var editedBoundary by remember(processingState.boundary) { mutableStateOf(processingState.boundary) }
    var cornerEditBaseline by remember(processingState.boundary) { mutableStateOf<MapBoundary?>(null) }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }
    var showHelp by rememberSaveable(processingState.sourceUri?.toString()) { mutableStateOf(showUsageTips) }
    var selectedPointId by rememberSaveable { mutableStateOf<String?>(null) }
    var newPointId by rememberSaveable { mutableStateOf<String?>(null) }
    var pointEditBaseline by remember { mutableStateOf<List<ControlPoint>?>(null) }
    var editedPoints by remember { mutableStateOf<List<ControlPoint>?>(null) }
    var confirmLineReset by rememberSaveable { mutableStateOf(false) }
    var confirmPointsClear by rememberSaveable { mutableStateOf(false) }
    var confirmLeaveEdit by rememberSaveable { mutableStateOf(false) }
    var confirmDiscardPointChanges by rememberSaveable { mutableStateOf(false) }
    var confirmDiscardCornerChanges by rememberSaveable { mutableStateOf(false) }
    var pendingBackAfterSave by rememberSaveable { mutableStateOf(false) }
    var viewportCenterPoint by remember(rectified) {
        mutableStateOf(Point2D(rectified.width / 2f, rectified.height / 2f))
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
    val visiblePoints = editedPoints ?: processingState.controlPoints
    val pointChangesPending = pointEditBaseline?.let { original -> visiblePoints != original } == true
    val cornerChangesPending = cornerEditBaseline?.let { original -> editedBoundary != original } == true
    val discardCornerChanges: () -> Unit = {
        editedBoundary = cornerEditBaseline ?: processingState.boundary
        cornerEditBaseline = null
        editMode = EditMode.CALIBRATION
    }
    val requestDiscardCornerChanges: () -> Unit = {
        if (cornerChangesPending) confirmDiscardCornerChanges = true else discardCornerChanges()
    }
    val discardPointChanges: () -> Unit = {
        pointEditBaseline = null
        editedPoints = null
        selectedPointId = null
        newPointId = null
        editMode = EditMode.CALIBRATION
    }
    val requestDiscardPointChanges: () -> Unit = {
        if (pointChangesPending) confirmDiscardPointChanges = true else discardPointChanges()
    }
    val savePointChanges: () -> Unit = {
        val original = pointEditBaseline ?: processingState.controlPoints
        val updated = editedPoints ?: original
        val originalById = original.associateBy(ControlPoint::id)
        val updatedIds = updated.mapTo(mutableSetOf(), ControlPoint::id)
        if (original.isNotEmpty() && updated.isEmpty()) {
            onClearControlPoints()
        } else {
            original.filterNot { it.id in updatedIds }.forEach { onRemoveControlPoint(it.id) }
            updated.forEach { point ->
                val previous = originalById[point.id]
                when {
                    previous == null -> onAddControlPoint(point)
                    previous != point -> onUpdateControlPoint(point)
                }
            }
        }
        pointEditBaseline = null
        editedPoints = null
        selectedPointId = null
        newPointId = null
        editMode = EditMode.CALIBRATION
    }
    val requestBack = {
        when (editMode) {
            EditMode.POINTS -> requestDiscardPointChanges()
            EditMode.CORNERS -> requestDiscardCornerChanges()
            EditMode.CALIBRATION -> when {
                isSaving -> pendingBackAfterSave = true
                isDirty -> confirmLeaveEdit = true
                else -> onBack()
            }
        }
    }
    BackHandler(onBack = requestBack)
    LaunchedEffect(isSaving, isDirty, pendingBackAfterSave) {
        if (!isSaving && pendingBackAfterSave) {
            pendingBackAfterSave = false
            if (isDirty) {
                confirmLeaveEdit = true
            } else {
                confirmLeaveEdit = false
                onBack()
            }
        }
    }

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
            action = when {
                editMode == EditMode.POINTS && visiblePoints.isNotEmpty() -> ({
                    IconButton(onClick = { confirmPointsClear = true }) {
                        Icon(
                            Icons.Default.DeleteForever,
                            contentDescription = stringResource(R.string.clear_points),
                        )
                    }
                })
                else -> null
            },
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
                            rotationOffsetDegrees = rotationOffsetDegrees,
                            rotationGesturesEnabled = rotationGesturesEnabled,
                            onRotationGesture = onRotationGesture,
                            recenterKey = recenterKey,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    EditMode.POINTS -> InteractiveControlPointCanvas(
                        bitmap = rectified,
                        points = visiblePoints,
                        rotationQuarterTurns = rotation,
                        rotationOffsetDegrees = rotationOffsetDegrees,
                        rotationGesturesEnabled = rotationGesturesEnabled,
                        onRotationGesture = onRotationGesture,
                        onPointMoved = { moved ->
                            editedPoints = visiblePoints.map { if (it.id == moved.id) moved else it }
                        },
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
                        rotationOffsetDegrees = rotationOffsetDegrees,
                        rotationGesturesEnabled = rotationGesturesEnabled,
                        onRotationGesture = onRotationGesture,
                        recenterKey = recenterKey,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                MapOverlayButtons(
                    onRotate = { onRotationChange(rotation + 1) },
                    onRecenter = { onSnapRotation(); recenterKey++ },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
                if (editMode == EditMode.POINTS) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        tonalElevation = 5.dp,
                    ) {
                        IconButton(
                            onClick = {
                                val point = ControlPoint(code = 0, center = viewportCenterPoint)
                                editedPoints = visiblePoints + point
                                selectedPointId = point.id
                                newPointId = point.id
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                Icons.Default.AddLocationAlt,
                                contentDescription = stringResource(R.string.add_point),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }

            when (editMode) {
                EditMode.CORNERS -> Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = requestDiscardCornerChanges,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.discard_changes), textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = {
                            val boundaryToApply = editedBoundary.takeIf { cornerChangesPending }
                            cornerEditBaseline = null
                            boundaryToApply?.let(onApplyBoundary)
                            editMode = EditMode.CALIBRATION
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.apply_corners), textAlign = TextAlign.Center)
                    }
                }

                EditMode.POINTS -> Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = requestDiscardPointChanges,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.discard_changes), textAlign = TextAlign.Center)
                    }
                    Button(onClick = savePointChanges, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.save_points), textAlign = TextAlign.Center)
                    }
                }

                EditMode.CALIBRATION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                cornerEditBaseline = processingState.boundary
                                editedBoundary = processingState.boundary
                                editMode = EditMode.CORNERS
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null)
                            Text(
                                stringResource(R.string.edit_corners),
                                modifier = Modifier.padding(start = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val points = processingState.controlPoints.map(ControlPoint::copy)
                                pointEditBaseline = points
                                editedPoints = points
                                editMode = EditMode.POINTS
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(
                                stringResource(R.string.edit_points),
                                modifier = Modifier.padding(start = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { confirmLineReset = true },
                            enabled = lineStart != null || lineEnd != null || lineDistanceText.isNotEmpty(),
                            modifier = Modifier.size(56.dp).offset(y = 2.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.reset_line),
                            )
                        }
                        OutlinedTextField(
                            value = lineDistanceText,
                            onValueChange = onLineDistanceChange,
                            enabled = lineEnd != null,
                            label = { Text(stringResource(R.string.line_distance)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        onCancel?.let { cancel ->
                            OutlinedButton(onClick = cancel, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.cancel), textAlign = TextAlign.Center)
                            }
                        }
                        Button(
                            enabled = effectivePixelsPerMeter != null,
                            onClick = onOpenRoute,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.open_route_view),
                                textAlign = TextAlign.Center,
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
    if (confirmDiscardPointChanges) {
        ConfirmationDialog(
            title = stringResource(R.string.discard_point_changes_title),
            message = stringResource(R.string.discard_point_changes_confirmation),
            onConfirm = {
                confirmDiscardPointChanges = false
                discardPointChanges()
            },
            onDismiss = { confirmDiscardPointChanges = false },
        )
    }
    if (confirmDiscardCornerChanges) {
        ConfirmationDialog(
            title = stringResource(R.string.discard_corner_changes_title),
            message = stringResource(R.string.discard_corner_changes_confirmation),
            onConfirm = {
                confirmDiscardCornerChanges = false
                discardCornerChanges()
            },
            onDismiss = { confirmDiscardCornerChanges = false },
        )
    }
    selectedPointId?.let { id ->
        visiblePoints.firstOrNull { it.id == id }?.let { point ->
            ControlPointEditorDialog(
                point = point,
                isNew = point.id == newPointId,
                onSave = { savedPoint ->
                    editedPoints = visiblePoints.map {
                        if (it.id == savedPoint.id) savedPoint else it
                    }
                    newPointId = null
                    selectedPointId = null
                },
                onDelete = {
                    editedPoints = visiblePoints.filterNot { it.id == point.id }
                    newPointId = null
                    selectedPointId = null
                },
                onDismiss = {
                    if (point.id == newPointId) {
                        editedPoints = visiblePoints.filterNot { it.id == point.id }
                        newPointId = null
                    }
                    selectedPointId = null
                },
            )
        }
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
                editedPoints = emptyList()
                selectedPointId = null
                newPointId = null
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
    val focusManager = LocalFocusManager.current
    var codeText by rememberSaveable(point.id) {
        mutableStateOf(if (isNew) "" else point.code.toString())
    }
    var type by rememberSaveable(point.id) { mutableStateOf(point.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clearFocusOnPointerDown(focusManager),
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
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var recenterKey by rememberSaveable { mutableIntStateOf(0) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSaveConfirmation by rememberSaveable { mutableStateOf(false) }
    var showUnsavedHomeConfirmation by rememberSaveable { mutableStateOf(false) }
    var pendingHomeAfterSave by rememberSaveable { mutableStateOf(false) }
    var showMapActions by rememberSaveable { mutableStateOf(false) }
    var showRouteMenu by rememberSaveable { mutableStateOf(false) }
    var showRouteEditor by rememberSaveable { mutableStateOf(false) }
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

    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                IconButton(
                    onClick = {
                        if (isSaving) {
                            pendingHomeAfterSave = true
                        } else if (isDirty || savedMapId == null) {
                            showUnsavedHomeConfirmation = true
                        } else {
                            onHome()
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Default.Home, contentDescription = stringResource(R.string.home))
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
                    DropdownMenu(
                        expanded = showMapActions,
                        onDismissRequest = { showMapActions = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename_route)) },
                            leadingIcon = {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                            },
                            enabled = savedMapId != null,
                            onClick = { showMapActions = false; showRename = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.save_map)) },
                            leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                            enabled = canSave && !isSaving,
                            onClick = { showMapActions = false; showSaveConfirmation = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_saved_route)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            enabled = savedMapId != null && !isDirty,
                            onClick = { showMapActions = false; showDeleteConfirmation = true },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_map)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMapActions = false
                                if (availableRoutes.isNotEmpty()) showEditMapRouteWarning = true else onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_map)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            enabled = canSave && !isExporting,
                            onClick = { showMapActions = false; onExport() },
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            RouteRenderingCanvas(
                bitmap = bitmap,
                route = displayedRoute?.path.orEmpty(),
                routeLayers = if (selectableRoutes.isEmpty()) emptyList() else availableRoutes.mapNotNull { candidate ->
                    val metadata = routeMetadata[candidate.id] ?: RouteMetadata()
                    val isActive = candidate.id == displayedRoute?.id
                    if (!isActive && !metadata.isDisplayed) return@mapNotNull null
                    RouteRenderLayer(
                        id = candidate.id,
                        points = candidate.path,
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
                modifier = Modifier.fillMaxSize(),
            )
            MapOverlayButtons(
                onRotate = { onRotationChange(rotation + 1) },
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
                                        onClick = { showRouteMenu = false; showRouteEditor = true },
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
                OutlinedButton(
                    onClick = { showDetails = true },
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
            onRouteSelected = { newRouteId ->
                if (newRouteId != selectedRouteId) {
                    selectedRouteId = newRouteId
                    onManageRoutes(RouteManagementAction.Select(newRouteId))
                    onRouteSelectionChanged()
                }
            },
            onDismissRequest = { showDetails = false },
        )
    }
    if (showRouteEditor && displayedRoute != null) {
        RouteEditorBottomSheet(
            route = displayedRoute,
            allPoints = allPoints,
            onSave = { editedPath ->
                pixelsPerMeter?.let { scale ->
                    onManageRoutes(
                        RouteManagementAction.UpdatePath(displayedRoute.id, editedPath, scale),
                    )
                }
                showRouteEditor = false
            },
            onDismissRequest = { showRouteEditor = false },
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
private fun RouteGenerationDialog(
    initialMode: RouteMode,
    initialBudgetMeters: Float,
    initialTargetScore: Int,
    isCalculating: Boolean,
    canCalculate: Boolean,
    dismissAsCancel: Boolean,
    onGenerate: (RouteMode, Float?, Int?) -> Unit,
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
                                            val nextColor = ((managedMetadata.values.maxOfOrNull(RouteMetadata::colorIndex) ?: -1) + 1) % 10
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
private fun managedRouteDefaultName(
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

@Composable
private fun RouteRestrictionsDialog(
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

private fun restrictionPointLabel(point: ControlPoint): String = when (point.type) {
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

@Composable
private fun AlternativeRoutesDialog(
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
    var useRelativeValues by rememberSaveable { mutableStateOf(false) }
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
        if (useRelativeValues) sourceRoute.totalDistanceMeters - it else it
    }
    val resolvedMaxDistance = maxDistance?.let {
        if (useRelativeValues) sourceRoute.totalDistanceMeters + it else it
    }
    val resolvedMinScore = minScore?.let { if (useRelativeValues) sourceRoute.totalScore - it else it }
    val resolvedMaxScore = maxScore?.let { if (useRelativeValues) sourceRoute.totalScore + it else it }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.use_relative_values),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Switch(
                        checked = useRelativeValues,
                        onCheckedChange = { useRelativeValues = it },
                        enabled = !isGenerating,
                    )
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.distance_bounds),
                    style = MaterialTheme.typography.labelLarge,
                )
                AlternativeCriterionField(
                    value = minDistanceText,
                    onValueChange = { minDistanceText = it },
                    label = stringResource(
                        if (useRelativeValues) R.string.distance_under_meters
                        else R.string.minimum_distance_meters,
                    ),
                    enabled = !isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )
                AlternativeCriterionField(
                    value = maxDistanceText,
                    onValueChange = { maxDistanceText = it },
                    label = stringResource(
                        if (useRelativeValues) R.string.distance_over_meters
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
                AlternativeCriterionField(
                    value = minScoreText,
                    onValueChange = { minScoreText = it },
                    label = stringResource(
                        if (useRelativeValues) R.string.points_under else R.string.minimum_points,
                    ),
                    enabled = !isGenerating,
                    integerOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AlternativeCriterionField(
                    value = maxScoreText,
                    onValueChange = { maxScoreText = it },
                    label = stringResource(
                        if (useRelativeValues) R.string.points_over else R.string.maximum_points,
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
                            useRelativeValues = useRelativeValues,
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
    onUpdateMapRotationGestures: (Boolean) -> Unit,
    onUpdatePointsPerKilometer: (Boolean) -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.map_rotation),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Switch(
                        checked = preferences.disableMapRotationGestures,
                        onCheckedChange = onUpdateMapRotationGestures,
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.points_per_kilometer),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Switch(
                        checked = preferences.showPointsPerKilometer,
                        onCheckedChange = onUpdatePointsPerKilometer,
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
                    )
                }
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
