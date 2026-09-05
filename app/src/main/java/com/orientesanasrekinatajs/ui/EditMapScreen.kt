package com.orientesanasrekinatajs.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.ui.components.DistanceCalibrationCanvas
import com.orientesanasrekinatajs.ui.components.ControlColorCalibrationCanvas
import com.orientesanasrekinatajs.ui.components.InteractiveCornerCanvas
import com.orientesanasrekinatajs.ui.components.InteractiveControlPointCanvas
import com.orientesanasrekinatajs.ui.processing.MapProcessingUiState
import kotlin.math.hypot

private enum class EditMode { CALIBRATION, CORNERS, POINTS }

@Composable
internal fun EditMapScreen(
    processingState: MapProcessingUiState,
    showUsageTips: Boolean,
    calibrationUsageTipsEnabled: Boolean,
    onUsageTipDismissed: () -> Unit,
    onApplyBoundary: (MapBoundary) -> Unit,
    onUpdateControlPoint: (ControlPoint) -> Unit,
    onAddControlPoint: (ControlPoint) -> Unit,
    onRemoveControlPoint: (String) -> Unit,
    onClearControlPoints: () -> Unit,
    onStartColorCalibration: () -> Unit,
    onApplyColorCalibrationSample: (Point2D) -> Unit,
    onConfirmColorCalibration: () -> Unit,
    onCancelColorCalibration: () -> Unit,
    onDismissProcessingError: () -> Unit,
    onDismissReviewSummary: () -> Unit,
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
    var confirmCalibrationStart by rememberSaveable { mutableStateOf(false) }
    var showCalibrationHelp by rememberSaveable(processingState.isCalibratingColor) {
        mutableStateOf(processingState.isCalibratingColor && calibrationUsageTipsEnabled)
    }
    if (processingState.isCalibratingColor) {
        BackHandler(onBack = onCancelColorCalibration)
        Column(Modifier.fillMaxSize()) {
            ScreenTopBar(
                title = stringResource(R.string.calibrate_control_color),
                onBack = onCancelColorCalibration,
            )
            ControlColorCalibrationCanvas(
                bitmap = rectified,
                referencePoints = processingState.calibrationReferencePoints,
                onPointSelected = onApplyColorCalibrationSample,
                rotationQuarterTurns = rotation,
                rotationOffsetDegrees = rotationOffsetDegrees,
                rotationGesturesEnabled = rotationGesturesEnabled,
                onRotationGesture = onRotationGesture,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCancelColorCalibration,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onConfirmColorCalibration,
                    enabled = processingState.pendingColorCalibration != null &&
                        !processingState.isSamplingColor,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
        if (showCalibrationHelp && processingState.error == null) {
            MessageDialog(
                title = stringResource(R.string.usage_tip_title),
                message = stringResource(R.string.calibrate_control_color_help),
                onDismiss = { showCalibrationHelp = false },
            )
        }
        processingState.error?.let { error ->
            MessageDialog(
                title = stringResource(R.string.calibrate_control_color_failed),
                message = error,
                onDismiss = onDismissProcessingError,
            )
        }
        return
    }
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
    val reviewPointCount = processingState.controlPoints.count(ControlPoint::needsReview)
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
                    onRotate = {
                        onRotationChange(nextClockwiseQuarterTurn(rotation, rotationOffsetDegrees))
                    },
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
                    OutlinedButton(
                        onClick = { confirmCalibrationStart = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.calibrate_control_color))
                    }
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

    if (showHelp && (reviewPointCount == 0 || processingState.reviewSummaryDismissed)) {
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

    if (confirmCalibrationStart) {
        ConfirmationDialog(
            title = stringResource(R.string.calibrate_control_color_warning_title),
            message = stringResource(R.string.calibrate_control_color_warning),
            onConfirm = {
                confirmCalibrationStart = false
                onStartColorCalibration()
            },
            onDismiss = { confirmCalibrationStart = false },
        )
    }

    if (reviewPointCount > 0 && !processingState.reviewSummaryDismissed) {
        MessageDialog(
            title = stringResource(R.string.points_need_review_title),
            message = pluralStringResource(
                R.plurals.points_need_review,
                reviewPointCount,
                reviewPointCount,
            ),
            onDismiss = onDismissReviewSummary,
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
internal fun MapOverlayButtons(
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
internal fun ControlPointEditorDialog(
    point: ControlPoint,
    isNew: Boolean,
    onSave: (ControlPoint) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var codeText by rememberSaveable(point.id) {
        mutableStateOf(if (isNew || point.needsReview) "" else point.code.toString())
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
                        modifier = Modifier.fillMaxWidth().testTag("controlPointCode"),
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
                    enabled = type != ControlPointType.CONTROL ||
                        (codeText.toIntOrNull()?.let { it >= 31 } == true),
                    onClick = {
                        val code = if (type == ControlPointType.CONTROL) codeText.toIntOrNull() ?: 0 else 0
                        onSave(
                            point.copy(
                                code = code,
                                points = if (type == ControlPointType.CONTROL) code / 10 else 0,
                                type = type,
                                needsReview = false,
                            ),
                        )
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
