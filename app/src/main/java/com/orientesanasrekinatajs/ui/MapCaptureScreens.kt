package com.orientesanasrekinatajs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.ui.components.InteractiveCornerCanvas
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun ProcessingScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BackButton(onBack, Modifier.align(Alignment.TopStart))
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.processing_map), modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
internal fun ErrorScreen(error: String, onReset: () -> Unit) {
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
internal fun ManualBoundaryScreen(
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
                onRotate = {
                    onRotationChange(nextClockwiseQuarterTurn(rotation, rotationOffsetDegrees))
                },
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

internal fun nextClockwiseQuarterTurn(
    rotationQuarterTurns: Int,
    rotationOffsetDegrees: Float,
): Int = floor((rotationQuarterTurns * 90f + rotationOffsetDegrees) / 90f + 0.0001f)
    .toInt() + 1

/** Chooses the cardinal orientation closest to the current free-rotation angle. */
internal fun nearestQuarterTurn(
    rotationQuarterTurns: Int,
    rotationOffsetDegrees: Float,
): Int = ((rotationQuarterTurns * 90f + rotationOffsetDegrees) / 90f).roundToInt()

/** Re-bases a free rotation onto a new quarter turn without changing its visible angle. */
