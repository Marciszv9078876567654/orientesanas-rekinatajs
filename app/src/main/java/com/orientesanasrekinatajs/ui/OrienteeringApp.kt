package com.orientesanasrekinatajs.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.data.transfer.PdfMapDraft
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import com.orientesanasrekinatajs.domain.model.UserPreferences
import com.orientesanasrekinatajs.ui.components.ImageSourceScreen
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    onStartColorCalibration: () -> Unit,
    onApplyColorCalibrationSample: (Point2D) -> Unit,
    onMoveColorCalibrationReference: (Int, Point2D) -> Unit,
    onClearColorCalibrationReferences: () -> Unit,
    onConfirmColorCalibration: () -> Unit,
    onCancelColorCalibration: () -> Unit,
    onDismissProcessingError: () -> Unit,
    onDismissReviewSummary: () -> Unit,
    onRestoreMapState: (MapProcessingUiState) -> Unit,
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
    onExportPdf: (Uri, PdfMapDraft) -> Unit,
    onImportMap: (Uri) -> Unit,
    onSaveMap: (SavedMapDraft, (ScannedMapEntity) -> Unit) -> Unit,
    onLoadSavedMap: (String) -> Unit,
    onRenameSavedMap: (String, String, (ScannedMapEntity) -> Unit) -> Unit,
    onDeleteSavedMap: (String, () -> Unit) -> Unit,
    onCopySavedMap: (String, String) -> Unit,
    onClearAllSavedMaps: () -> Unit,
    onDismissSavedMapsEvent: () -> Unit,
    onDismissMapTransferEvent: () -> Unit,
    onReset: () -> Unit,
    onUpdateTheme: (ThemeConfig) -> Unit,
    onUpdateLanguage: (LanguageConfig) -> Unit,
    onUpdateAnimations: (Boolean) -> Unit,
    onUpdateMapRotationGestures: (Boolean) -> Unit,
    onUpdateUsageTips: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val animationsEnabled = LocalAnimationsEnabled.current
    val rotationAnimationScope = rememberCoroutineScope()
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
    var rotationAnimationJob by remember(rotationSessionKey) { mutableStateOf<Job?>(null) }
    val applyRotationGesture: (Float) -> Unit = { change ->
        if (userPreferences.disableMapRotationGestures && change.isFinite()) {
            rotationAnimationJob?.cancel()
            rotationAnimationJob = null
            mapRotationOffsetDegrees = (mapRotationOffsetDegrees + change) % 360f
        }
    }
    val animateMapRotationTo: (Int) -> Unit = { quarterTurns ->
        rotationAnimationJob?.cancel()
        val startingOffset = rotationOffsetPreservingAngle(
            mapRotation,
            mapRotationOffsetDegrees,
            quarterTurns,
        )
        mapRotation = quarterTurns
        mapRotationOffsetDegrees = startingOffset
        if (!animationsEnabled) {
            mapRotationOffsetDegrees = 0f
            rotationAnimationJob = null
        } else {
            rotationAnimationJob = rotationAnimationScope.launch {
                animate(
                    initialValue = startingOffset,
                    targetValue = 0f,
                    animationSpec = tween(180),
                ) { value, _ -> mapRotationOffsetDegrees = value }
                rotationAnimationJob = null
            }
        }
    }
    val snapMapRotation: () -> Unit = {
        animateMapRotationTo(nearestQuarterTurn(mapRotation, mapRotationOffsetDegrees))
    }
    val setMapRotation: (Int) -> Unit = animateMapRotationTo
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
                processingState.error != null && processingState.stage != MapProcessingStage.COMPLETE ->
                    ErrorScreen(processingState.error, onReset)
                processingState.stage == MapProcessingStage.COMPLETE -> MapFlowScreen(
                    processingState = processingState,
                    routingState = routingState,
                    defaultBudgetMeters = userPreferences.defaultDistanceBudgetKm * 1_000f,
                    showUsageTips = userPreferences.showUsageTips &&
                        usageTipDismissedForUri != processingState.sourceUri?.toString(),
                    calibrationUsageTipsEnabled = userPreferences.showUsageTips,
                    onUsageTipDismissed = {
                        usageTipDismissedForUri = processingState.sourceUri?.toString()
                    },
                    onApplyEditedBoundary = onApplyEditedBoundary,
                    onUpdateControlPoint = onUpdateControlPoint,
                    onAddControlPoint = onAddControlPoint,
                    onRemoveControlPoint = onRemoveControlPoint,
                    onClearControlPoints = onClearControlPoints,
                    onStartColorCalibration = onStartColorCalibration,
                    onApplyColorCalibrationSample = onApplyColorCalibrationSample,
                    onMoveColorCalibrationReference = onMoveColorCalibrationReference,
                    onClearColorCalibrationReferences = onClearColorCalibrationReferences,
                    onConfirmColorCalibration = onConfirmColorCalibration,
                    onCancelColorCalibration = onCancelColorCalibration,
                    onDismissProcessingError = onDismissProcessingError,
                    onDismissReviewSummary = onDismissReviewSummary,
                    onRestoreMapState = onRestoreMapState,
                    onInvalidateRoute = onInvalidateRoute,
                    onCalculateRoute = onCalculateRoute,
                    onGenerateAlternativeRoutes = onGenerateAlternativeRoutes,
                    onManageRoutes = onManageRoutes,
                    onManageRouteRestrictions = onManageRouteRestrictions,
                    onExportMap = onExportMap,
                    onExportPdf = onExportPdf,
                    onSaveMap = onSaveMap,
                    onRenameSavedMap = onRenameSavedMap,
                    onDeleteSavedMap = onDeleteSavedMap,
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
            onRename = { id, name -> onRenameSavedMap(id, name) {} },
            onDelete = { id -> onDeleteSavedMap(id) {} },
            onCopy = onCopySavedMap,
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
                    SavedMapsEvent.COPY_FAILED -> R.string.map_copy_failed
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
                if (event == MapTransferEvent.EXPORTED || event == MapTransferEvent.PDF_EXPORTED) {
                    R.string.map_exported_title
                }
                else R.string.map_transfer_error_title,
            ),
            message = stringResource(
                when (event) {
                    MapTransferEvent.EXPORTED -> R.string.map_exported_message
                    MapTransferEvent.PDF_EXPORTED -> R.string.pdf_exported_message
                    MapTransferEvent.EXPORT_FAILED -> R.string.map_export_failed
                    MapTransferEvent.IMPORT_FAILED -> R.string.map_import_failed
                },
            ),
            onDismiss = onDismissMapTransferEvent,
        )
    }
}

internal fun Modifier.clearFocusOnPointerDown(focusManager: FocusManager): Modifier = pointerInput(focusManager) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        focusManager.clearFocus(force = true)
    }
}

@Composable
internal fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onBack, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

@Composable
internal fun ScreenTopBar(
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


/** Returns the next cardinal orientation strictly clockwise from the current angle. */
internal fun rotationOffsetPreservingAngle(
    currentQuarterTurns: Int,
    currentOffsetDegrees: Float,
    targetQuarterTurns: Int,
): Float = currentQuarterTurns * 90f + currentOffsetDegrees - targetQuarterTurns * 90f
