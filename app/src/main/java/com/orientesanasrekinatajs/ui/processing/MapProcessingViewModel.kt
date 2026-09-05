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
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.imageprocessing.ColorCalibrationSample
import com.orientesanasrekinatajs.imageprocessing.DetectedControlSymbol
import com.orientesanasrekinatajs.imageprocessing.ImageCropUtils
import com.orientesanasrekinatajs.imageprocessing.OcrUtils
import com.orientesanasrekinatajs.imageprocessing.OpenCVUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val manualBoundaryRequired: Boolean = false,
    val isCalibratingColor: Boolean = false,
    val colorCalibration: ColorCalibrationSample? = null,
    val pendingColorCalibration: ColorCalibrationSample? = null,
    val calibrationReferencePoints: List<Point2D> = emptyList(),
    val calibrationReferenceSamples: List<ColorCalibrationSample> = emptyList(),
    val isSamplingColor: Boolean = false,
    val reviewSummaryDismissed: Boolean = false,
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
                if (point.id == updated.id) updated.copy(needsReview = false) else point
            },
            savedContentDirty = true,
        )
    }

    fun addControlPoint(point: ControlPoint) {
        _uiState.value = _uiState.value.copy(
            controlPoints = _uiState.value.controlPoints + point.copy(needsReview = false),
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

    /** Enters the single-tap control-ink calibration mode on the rectified map. */
    fun startColorCalibration() {
        if (_uiState.value.rectifiedBitmap == null) return
        _uiState.value = _uiState.value.copy(
            isCalibratingColor = true,
            pendingColorCalibration = null,
            calibrationReferencePoints = emptyList(),
            calibrationReferenceSamples = emptyList(),
            isSamplingColor = false,
            error = null,
        )
    }

    fun cancelColorCalibration() {
        processingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isCalibratingColor = false,
            pendingColorCalibration = null,
            calibrationReferencePoints = emptyList(),
            calibrationReferenceSamples = emptyList(),
            isSamplingColor = false,
            error = null,
        )
    }

    /** Detects and applies the replacement point set after reference selection is confirmed. */
    fun confirmColorCalibration() {
        val current = _uiState.value
        val sample = current.pendingColorCalibration ?: return
        val rectified = current.rectifiedBitmap ?: return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _uiState.value = current.copy(isSamplingColor = true, error = null)
            runCatching {
                withContext(workerDispatcher) {
                    val symbols = engine.detectControlSymbols(rectified, sample)
                    val detected = recognizeControlPoints(rectified, symbols, sample)
                    if (detected.isEmpty()) {
                        _uiState.value = current.copy(
                            isCalibratingColor = true,
                            isSamplingColor = false,
                            error = "No matching control circles were found. Try another reference.",
                        )
                        return@withContext
                    }
                    _uiState.value = current.copy(
                        controlPoints = detected,
                        colorCalibration = sample,
                        pendingColorCalibration = null,
                        calibrationReferencePoints = emptyList(),
                        calibrationReferenceSamples = emptyList(),
                        isCalibratingColor = false,
                        isSamplingColor = false,
                        savedContentDirty = true,
                        error = null,
                        reviewSummaryDismissed = detected.none(ControlPoint::needsReview),
                    )
                }
            }.onFailure { throwable ->
                if (throwable !is kotlinx.coroutines.CancellationException) {
                    _uiState.value = current.copy(
                        isCalibratingColor = true,
                        isSamplingColor = false,
                        error = throwable.message ?: "Control color calibration failed",
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissReviewSummary() {
        _uiState.value = _uiState.value.copy(reviewSummaryDismissed = true)
    }

    /** Samples one reference circle without running full-map detection or OCR. */
    fun applyColorCalibrationSample(tapPoint: Point2D) {
        val current = _uiState.value
        val rectified = current.rectifiedBitmap ?: return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _uiState.value = current.copy(isSamplingColor = true, error = null)
            runCatching {
                withContext(workerDispatcher) {
                    val sample = engine.sampleControlPointColor(rectified, tapPoint)
                    if (sample == null) {
                        _uiState.value = current.copy(
                            isCalibratingColor = true,
                            isSamplingColor = false,
                            error = "Couldn't identify a control symbol there. Try tapping more precisely.",
                        )
                        return@withContext
                    }
                    val existingReferences = current.calibrationReferencePoints
                    val isDuplicate = existingReferences.any { reference ->
                        reference.distanceTo(tapPoint) <= sample.estimatedRadius * 0.75f
                    }
                    val references = if (isDuplicate) {
                        existingReferences
                    } else {
                        existingReferences + tapPoint
                    }
                    val samples = if (isDuplicate) {
                        current.calibrationReferenceSamples
                    } else {
                        current.calibrationReferenceSamples + sample
                    }
                    _uiState.value = current.copy(
                        pendingColorCalibration = combineCalibrationSamples(samples),
                        calibrationReferencePoints = references,
                        calibrationReferenceSamples = samples,
                        isCalibratingColor = true,
                        isSamplingColor = false,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                if (throwable !is kotlinx.coroutines.CancellationException) {
                    _uiState.value = current.copy(
                        stage = MapProcessingStage.COMPLETE,
                        isCalibratingColor = true,
                        isSamplingColor = false,
                        error = throwable.message ?: "Control color calibration failed",
                    )
                }
            }
        }
    }

    /** Moves a selected reference immediately and re-samples after drag events settle. */
    fun moveColorCalibrationReference(index: Int, center: Point2D) {
        val current = _uiState.value
        if (index !in current.calibrationReferencePoints.indices) return
        val movedReferences = current.calibrationReferencePoints.toMutableList().apply {
            this[index] = center
        }
        _uiState.value = current.copy(
            calibrationReferencePoints = movedReferences,
            isSamplingColor = true,
            error = null,
        )
        val rectified = current.rectifiedBitmap ?: return
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            delay(180)
            runCatching {
                withContext(workerDispatcher) {
                    val sample = engine.sampleControlPointColor(rectified, center) ?: run {
                        val latest = _uiState.value
                        val references = latest.calibrationReferencePoints
                            .filterIndexed { sampleIndex, _ -> sampleIndex != index }
                        val samples = latest.calibrationReferenceSamples
                            .filterIndexed { sampleIndex, _ -> sampleIndex != index }
                        _uiState.value = latest.copy(
                            pendingColorCalibration = combineCalibrationSamples(samples),
                            calibrationReferencePoints = references,
                            calibrationReferenceSamples = samples,
                            isSamplingColor = false,
                            error = "Couldn't identify a control symbol there. Move it closer to the circle center.",
                        )
                        return@withContext
                    }
                    val latest = _uiState.value
                    if (index !in latest.calibrationReferenceSamples.indices) return@withContext
                    val samples = latest.calibrationReferenceSamples.toMutableList().apply {
                        this[index] = sample
                    }
                    _uiState.value = latest.copy(
                        pendingColorCalibration = combineCalibrationSamples(samples),
                        calibrationReferenceSamples = samples,
                        isSamplingColor = false,
                        error = null,
                    )
                }
            }.onFailure { throwable ->
                if (throwable !is kotlinx.coroutines.CancellationException) {
                    _uiState.value = _uiState.value.copy(
                        isSamplingColor = false,
                        error = throwable.message ?: "Control color calibration failed",
                    )
                }
            }
        }
    }

    fun clearColorCalibrationReferences() {
        processingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            pendingColorCalibration = null,
            calibrationReferencePoints = emptyList(),
            calibrationReferenceSamples = emptyList(),
            isSamplingColor = false,
            error = null,
        )
    }

    private fun combineCalibrationSamples(
        samples: List<ColorCalibrationSample>,
    ): ColorCalibrationSample? {
        if (samples.isEmpty()) return null
        fun median(values: List<Double>): Double = values.sorted()[values.size / 2]
        val hueCenters = samples.map { (it.hueRange.start + it.hueRange.endInclusive) / 2.0 }
        val hueHalfWidths = samples.map {
            (it.hueRange.endInclusive - it.hueRange.start) / 2.0
        }
        val hueCenter = median(hueCenters)
        val hueHalfWidth = median(hueHalfWidths)
        return ColorCalibrationSample(
            hueRange = (hueCenter - hueHalfWidth).coerceAtLeast(0.0)..
                (hueCenter + hueHalfWidth).coerceAtMost(179.0),
            saturationRange = median(samples.map { it.saturationRange.start })..
                median(samples.map { it.saturationRange.endInclusive }),
            valueRange = median(samples.map { it.valueRange.start })..
                median(samples.map { it.valueRange.endInclusive }),
            estimatedRadius = median(samples.map { it.estimatedRadius.toDouble() }).toFloat(),
        )
    }

    /** Opens a transferred map as a new unsaved working copy. */
    fun openImportedMap(imported: SavedMap) {
        openSavedMap(imported)
        _uiState.value = _uiState.value.copy(
            savedMapId = null,
            savedMapName = imported.name,
            savedContentDirty = true,
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
        val colorCalibration = _uiState.value.colorCalibration
        val symbols = try {
            engine.detectControlSymbols(rectified, colorCalibration)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Automatic point detection is optional: keep the rectified map usable so points
            // can still be added manually on devices where the detector cannot initialize.
            emptyList()
        }
        updateStage(MapProcessingStage.RECOGNIZING_CODES, source, boundary, rectified)

        val points = recognizeControlPoints(rectified, symbols, colorCalibration)

        _uiState.value = MapProcessingUiState(
            sourceUri = sourceUri,
            sourceBitmap = source,
            boundary = boundary,
            rectifiedBitmap = rectified,
            controlPoints = points,
            colorCalibration = colorCalibration,
            stage = MapProcessingStage.COMPLETE,
        )
    }

    private suspend fun detectControlPoints(
        rectified: Bitmap,
        colorCalibration: ColorCalibrationSample?,
    ): List<ControlPoint> {
        val symbols = engine.detectControlSymbols(rectified, colorCalibration)
        _uiState.value = _uiState.value.copy(stage = MapProcessingStage.RECOGNIZING_CODES)
        return recognizeControlPoints(rectified, symbols, colorCalibration)
    }

    private suspend fun recognizeControlPoints(
        rectified: Bitmap,
        symbols: List<DetectedControlSymbol>,
        colorCalibration: ColorCalibrationSample?,
    ): List<ControlPoint> {
        val points = symbols.map { symbol ->
            var needsReview = false
            val code = if (symbol.type == ControlPointType.CONTROL) {
                try {
                    val roi = engine.cropRegion(rectified, symbol)
                    try {
                        val isolated = engine.isolateInkColor(roi, colorCalibration)
                        try {
                            engine.extractControlNumber(isolated) ?: run {
                                needsReview = true
                                0
                            }
                        } finally {
                            if (isolated !== roi && isolated !== rectified && !isolated.isRecycled) {
                                isolated.recycle()
                            }
                        }
                    } finally {
                        if (roi !== rectified && !roi.isRecycled) roi.recycle()
                    }
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // OCR availability varies by device. Keep the detected point editable when
                    // ML Kit cannot initialize or recognize this individual crop.
                    needsReview = true
                    0
                }
            } else {
                0
            }
            ControlPoint(
                code = code,
                points = if (symbol.type == ControlPointType.CONTROL) code / 10 else 0,
                center = symbol.center,
                type = symbol.type,
                needsReview = needsReview,
            )
        }
        val duplicateCodes = points.asSequence()
            .filter { it.type == ControlPointType.CONTROL && it.code != 0 }
            .groupBy(ControlPoint::code)
            .filterValues { matches -> matches.size > 1 }
            .keys
        return points.map { point ->
            if (point.type == ControlPointType.CONTROL && point.code in duplicateCodes) {
                point.copy(needsReview = true)
            } else {
                point
            }
        }
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
        val failedDuringBoundaryDetection =
            _uiState.value.stage == MapProcessingStage.DETECTING_BOUNDARY
        _uiState.value = _uiState.value.copy(
            stage = MapProcessingStage.IDLE,
            error = throwable.message ?: "Map processing failed",
            manualBoundaryRequired = failedDuringBoundaryDetection,
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
    fun detectControlSymbols(
        bitmap: Bitmap,
        colorCalibration: ColorCalibrationSample?,
    ): List<DetectedControlSymbol> = detectControlSymbols(bitmap)
    fun sampleControlPointColor(bitmap: Bitmap, tapPoint: Point2D): ColorCalibrationSample? = null
    fun isolateInkColor(bitmap: Bitmap, colorCalibration: ColorCalibrationSample?): Bitmap = bitmap
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

    override fun detectControlSymbols(
        bitmap: Bitmap,
        colorCalibration: ColorCalibrationSample?,
    ): List<DetectedControlSymbol> = OpenCVUtils.detectControlSymbols(
        bitmap = bitmap,
        colorCalibration = colorCalibration,
    )

    override fun sampleControlPointColor(
        bitmap: Bitmap,
        tapPoint: Point2D,
    ): ColorCalibrationSample? = OpenCVUtils.sampleControlPointColor(bitmap, tapPoint)

    override fun isolateInkColor(
        bitmap: Bitmap,
        colorCalibration: ColorCalibrationSample?,
    ): Bitmap = OpenCVUtils.isolateInkColor(bitmap, colorCalibration)

    override fun cropRegion(bitmap: Bitmap, symbol: DetectedControlSymbol): Bitmap =
        ImageCropUtils.cropRegionOfInterest(bitmap, symbol.center, symbol.radius.coerceAtLeast(4f))

    override suspend fun extractControlNumber(bitmap: Bitmap): Int? =
        OcrUtils.extractControlNumber(bitmap)
}

private class MapProcessingException(message: String) : IllegalStateException(message)
