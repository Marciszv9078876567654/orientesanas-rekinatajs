package com.orientesanasrekinatajs.ui.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.data.local.SavedMap
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.imageprocessing.DetectedControlSymbol
import com.orientesanasrekinatajs.imageprocessing.ImageCropUtils
import com.orientesanasrekinatajs.imageprocessing.OcrUtils
import com.orientesanasrekinatajs.imageprocessing.OpenCVUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MapProcessingUiState(
    val sourceUri: Uri? = null,
    val sourceBitmap: Bitmap? = null,
    val boundary: MapBoundary? = null,
    val rectifiedBitmap: Bitmap? = null,
    val controlPoints: List<ControlPoint> = emptyList(),
    val savedPixelsPerMeter: Float? = null,
    val savedMapId: String? = null,
    val savedMapName: String? = null,
    val savedLineStart: com.orientesanasrekinatajs.domain.model.Point2D? = null,
    val savedLineEnd: com.orientesanasrekinatajs.domain.model.Point2D? = null,
    val savedLineDistanceMeters: Float? = null,
    val savedRotationQuarterTurns: Int = 0,
    val savedContentDirty: Boolean = false,
    val stage: MapProcessingStage = MapProcessingStage.IDLE,
    val error: String? = null,
) {
    val isProcessing: Boolean
        get() = stage !in setOf(MapProcessingStage.IDLE, MapProcessingStage.COMPLETE)
}

enum class MapProcessingStage {
    IDLE,
    LOADING_IMAGE,
    DETECTING_BOUNDARY,
    RECTIFYING_MAP,
    DETECTING_CONTROLS,
    RECOGNIZING_CODES,
    COMPLETE,
}

/** Coordinates bitmap decoding, OpenCV detection, rectification, cropping, and ML Kit OCR. */
class MapProcessingViewModel internal constructor(
    private val engine: MapProcessingEngine,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapProcessingUiState())
    val uiState: StateFlow<MapProcessingUiState> = _uiState.asStateFlow()
    val controlPoints: StateFlow<List<ControlPoint>> = uiState
        .map { state -> state.controlPoints }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var processingJob: Job? = null

    fun processImage(uri: Uri) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _uiState.value = MapProcessingUiState(
                sourceUri = uri,
                stage = MapProcessingStage.LOADING_IMAGE,
            )
            runCatching {
                withContext(workerDispatcher) {
                    val source = engine.decode(uri)
                    updateStage(MapProcessingStage.DETECTING_BOUNDARY, sourceBitmap = source)
                    val boundary = engine.detectBoundary(source)
                        ?: throw MapProcessingException("No map boundary was detected")
                    processBoundary(source, uri, boundary)
                }
            }.onFailure(::publishFailure)
        }
    }

    /** Re-runs rectification and recognition after the user adjusts the detected corners. */
    fun applyBoundary(boundary: MapBoundary) {
        val current = _uiState.value
        val source = current.sourceBitmap ?: return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            runCatching {
                withContext(workerDispatcher) {
                    processBoundary(source, current.sourceUri, boundary)
                }
            }.onFailure(::publishFailure)
        }
    }

    /** Re-rectifies an edited map while projecting the user's existing points into the new crop. */
    fun applyEditedBoundary(
        boundary: MapBoundary,
        lineStart: com.orientesanasrekinatajs.domain.model.Point2D?,
        lineEnd: com.orientesanasrekinatajs.domain.model.Point2D?,
        lineDistanceMeters: Float?,
    ) {
        val current = _uiState.value
        val source = current.sourceBitmap ?: return
        val previousBoundary = current.boundary ?: return applyBoundary(boundary)
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            runCatching {
                withContext(workerDispatcher) {
                    updateStage(MapProcessingStage.RECTIFYING_MAP, source, boundary)
                    val rectified = engine.warpPerspective(source, boundary)
                    val projectedCenters = OpenCVUtils.reprojectPoints(
                        current.controlPoints.map { it.center },
                        previousBoundary,
                        boundary,
                    )
                    val projectedLineStart = lineStart?.let {
                        OpenCVUtils.reprojectPoints(listOf(it), previousBoundary, boundary).single()
                    }
                    val projectedLineEnd = lineEnd?.let {
                        OpenCVUtils.reprojectPoints(listOf(it), previousBoundary, boundary).single()
                    }
                    val validLineDistance = lineDistanceMeters?.takeIf {
                        projectedLineStart != null && projectedLineEnd != null && it > 0f
                    }
                    val projectedScale = validLineDistance?.let { distance ->
                        kotlin.math.hypot(
                            projectedLineEnd!!.x - projectedLineStart!!.x,
                            projectedLineEnd.y - projectedLineStart.y,
                        ) / distance
                    }
                    _uiState.value = current.copy(
                        boundary = boundary,
                        rectifiedBitmap = rectified,
                        controlPoints = current.controlPoints.zip(projectedCenters).mapNotNull { (point, center) ->
                            center?.let { point.copy(center = it) }
                        },
                        savedPixelsPerMeter = projectedScale,
                        savedLineStart = projectedLineStart,
                        savedLineEnd = projectedLineEnd,
                        savedLineDistanceMeters = validLineDistance,
                        savedContentDirty = true,
                        stage = MapProcessingStage.COMPLETE,
                        error = null,
                    )
                }
            }.onFailure(::publishFailure)
        }
    }

    fun updateControlPoint(updated: ControlPoint) {
        val current = _uiState.value
        _uiState.value = current.copy(
            controlPoints = current.controlPoints.map { point ->
                if (point.id == updated.id) updated else point
            },
            savedContentDirty = true,
        )
    }

    fun addControlPoint(point: ControlPoint) {
        _uiState.value = _uiState.value.copy(
            controlPoints = _uiState.value.controlPoints + point,
            savedContentDirty = true,
        )
    }

    fun removeControlPoint(id: String) {
        _uiState.value = _uiState.value.copy(
            controlPoints = _uiState.value.controlPoints.filterNot { it.id == id },
            savedContentDirty = true,
        )
    }

    fun clearControlPoints() {
        _uiState.value = _uiState.value.copy(controlPoints = emptyList(), savedContentDirty = true)
    }

    /** Opens an already rectified persisted map without rerunning image recognition. */
    fun openSavedMap(saved: SavedMap) {
        processingJob?.cancel()
        _uiState.value = MapProcessingUiState(
            sourceBitmap = saved.bitmap,
            boundary = MapBoundary(
                topLeft = com.orientesanasrekinatajs.domain.model.Point2D(0f, 0f),
                topRight = com.orientesanasrekinatajs.domain.model.Point2D(saved.bitmap.width.toFloat(), 0f),
                bottomRight = com.orientesanasrekinatajs.domain.model.Point2D(
                    saved.bitmap.width.toFloat(), saved.bitmap.height.toFloat(),
                ),
                bottomLeft = com.orientesanasrekinatajs.domain.model.Point2D(0f, saved.bitmap.height.toFloat()),
            ),
            rectifiedBitmap = saved.bitmap,
            controlPoints = saved.points,
            savedPixelsPerMeter = saved.pixelsPerMeter,
            savedMapId = saved.id,
            savedMapName = saved.name,
            savedLineStart = saved.lineStart,
            savedLineEnd = saved.lineEnd,
            savedLineDistanceMeters = saved.lineDistanceMeters,
            savedRotationQuarterTurns = saved.rotationQuarterTurns,
            stage = MapProcessingStage.COMPLETE,
        )
    }

    fun markSaved(entity: ScannedMapEntity, draft: SavedMapDraft) {
        _uiState.value = _uiState.value.copy(
            savedPixelsPerMeter = entity.pixelsPerMeter,
            savedMapId = entity.id,
            savedMapName = entity.name,
            savedLineStart = draft.lineStart,
            savedLineEnd = draft.lineEnd,
            savedLineDistanceMeters = draft.lineDistanceMeters,
            savedRotationQuarterTurns = draft.rotationQuarterTurns,
            savedContentDirty = false,
        )
    }

    fun renameSavedMap(name: String) {
        _uiState.value = _uiState.value.copy(savedMapName = name)
    }

    fun reset() {
        processingJob?.cancel()
        _uiState.value = MapProcessingUiState()
    }

    private suspend fun processBoundary(
        source: Bitmap,
        sourceUri: Uri?,
        boundary: MapBoundary,
    ) {
        updateStage(MapProcessingStage.RECTIFYING_MAP, source, boundary)
        val rectified = engine.warpPerspective(source, boundary)
        updateStage(MapProcessingStage.DETECTING_CONTROLS, source, boundary, rectified)
        val symbols = engine.detectControlSymbols(rectified)
        updateStage(MapProcessingStage.RECOGNIZING_CODES, source, boundary, rectified)

        val points = symbols.map { symbol ->
            val code = if (symbol.type == ControlPointType.CONTROL) {
                val roi = engine.cropRegion(rectified, symbol)
                try {
                    engine.extractControlNumber(roi) ?: 0
                } finally {
                    if (roi !== rectified && !roi.isRecycled) roi.recycle()
                }
            } else {
                0
            }
            ControlPoint(
                code = code,
                points = if (symbol.type == ControlPointType.CONTROL) code / 10 else 0,
                center = symbol.center,
                type = symbol.type,
            )
        }

        _uiState.value = MapProcessingUiState(
            sourceUri = sourceUri,
            sourceBitmap = source,
            boundary = boundary,
            rectifiedBitmap = rectified,
            controlPoints = points,
            stage = MapProcessingStage.COMPLETE,
        )
    }

    private fun updateStage(
        stage: MapProcessingStage,
        sourceBitmap: Bitmap? = _uiState.value.sourceBitmap,
        boundary: MapBoundary? = _uiState.value.boundary,
        rectifiedBitmap: Bitmap? = _uiState.value.rectifiedBitmap,
    ) {
        _uiState.value = _uiState.value.copy(
            sourceBitmap = sourceBitmap,
            boundary = boundary,
            rectifiedBitmap = rectifiedBitmap,
            controlPoints = emptyList(),
            stage = stage,
            error = null,
        )
    }

    private fun publishFailure(throwable: Throwable) {
        if (throwable is kotlinx.coroutines.CancellationException) return
        _uiState.value = _uiState.value.copy(
            stage = MapProcessingStage.IDLE,
            error = throwable.message ?: "Map processing failed",
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val engine = AndroidMapProcessingEngine(context.applicationContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MapProcessingViewModel(engine) as T
            }
        }
    }
}

internal interface MapProcessingEngine {
    fun decode(uri: Uri): Bitmap
    fun detectBoundary(bitmap: Bitmap): MapBoundary?
    fun warpPerspective(bitmap: Bitmap, boundary: MapBoundary): Bitmap
    fun detectControlSymbols(bitmap: Bitmap): List<DetectedControlSymbol>
    fun cropRegion(bitmap: Bitmap, symbol: DetectedControlSymbol): Bitmap
    suspend fun extractControlNumber(bitmap: Bitmap): Int?
}

private class AndroidMapProcessingEngine(context: Context) : MapProcessingEngine {
    private val contentResolver = context.contentResolver

    override fun decode(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(
        ImageDecoder.createSource(contentResolver, uri),
    ) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    override fun detectBoundary(bitmap: Bitmap): MapBoundary? =
        OpenCVUtils.detectBoundaries(bitmap)

    override fun warpPerspective(bitmap: Bitmap, boundary: MapBoundary): Bitmap =
        OpenCVUtils.warpPerspective(bitmap, boundary)

    override fun detectControlSymbols(bitmap: Bitmap): List<DetectedControlSymbol> =
        OpenCVUtils.detectControlSymbols(bitmap)

    override fun cropRegion(bitmap: Bitmap, symbol: DetectedControlSymbol): Bitmap =
        ImageCropUtils.cropRegionOfInterest(bitmap, symbol.center, symbol.radius.coerceAtLeast(4f))

    override suspend fun extractControlNumber(bitmap: Bitmap): Int? =
        OcrUtils.extractControlNumber(bitmap)
}

private class MapProcessingException(message: String) : IllegalStateException(message)
