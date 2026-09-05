package com.orientesanasrekinatajs.ui

import android.graphics.Bitmap
import android.net.Uri
import com.orientesanasrekinatajs.data.local.SavedMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.imageprocessing.DetectedControlSymbol
import com.orientesanasrekinatajs.imageprocessing.ColorCalibrationSample
import com.orientesanasrekinatajs.ui.processing.MapProcessingEngine
import com.orientesanasrekinatajs.ui.processing.MapProcessingStage
import com.orientesanasrekinatajs.ui.processing.MapProcessingViewModel
import com.orientesanasrekinatajs.ui.routing.RouteMode
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction
import com.orientesanasrekinatajs.ui.routing.RoutingViewModel
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.domain.model.RouteRestrictionType
import com.orientesanasrekinatajs.ui.routing.AlternativeRouteCriteria
import com.orientesanasrekinatajs.ui.routing.RouteRestrictionAction
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase8ViewModelTest {

    @Test
    fun mapProcessing_runsCompletePipelineAndPublishesControlPoints() {
        val engine = FakeProcessingEngine()
        val viewModel = MapProcessingViewModel(engine, Dispatchers.Unconfined)

        onMainThread { viewModel.processImage(Uri.parse("content://test/map")) }

        val state = viewModel.uiState.value
        assertEquals(MapProcessingStage.COMPLETE, state.stage)
        assertEquals(listOf("decode", "boundary", "warp", "symbols", "crop", "ocr"), engine.calls)
        assertEquals(
            listOf(ControlPointType.START, ControlPointType.CONTROL, ControlPointType.FINISH),
            state.controlPoints.map(ControlPoint::type),
        )
        assertEquals(65, state.controlPoints[1].code)
        assertEquals(6, state.controlPoints[1].points)
    }

    @Test
    fun mapProcessing_surfacesMissingBoundaryAsRecoverableError() {
        val engine = FakeProcessingEngine(boundaryResult = null)
        val viewModel = MapProcessingViewModel(engine, Dispatchers.Unconfined)

        onMainThread { viewModel.processImage(Uri.parse("content://test/no-boundary")) }

        assertEquals(MapProcessingStage.IDLE, viewModel.uiState.value.stage)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("boundary"))
        assertTrue(viewModel.uiState.value.manualBoundaryRequired)
    }

    @Test
    fun mapProcessing_keepsDetectedPointWhenOcrFailsOnDevice() {
        val viewModel = MapProcessingViewModel(
            FakeProcessingEngine(ocrFailure = NullPointerException("ML Kit unavailable")),
            Dispatchers.Unconfined,
        )

        onMainThread { viewModel.processImage(Uri.parse("content://test/ocr-failure")) }

        val state = viewModel.uiState.value
        assertEquals(MapProcessingStage.COMPLETE, state.stage)
        assertEquals(null, state.error)
        assertTrue(!state.manualBoundaryRequired)
        val control = state.controlPoints.single { it.type == ControlPointType.CONTROL }
        assertEquals(0, control.code)
        assertTrue(control.needsReview)
    }

    @Test
    fun mapProcessing_flagsMissingAndDuplicateRecognizedCodes() {
        val symbols = listOf(
            DetectedControlSymbol(Point2D(20f, 20f), 5f, ControlPointType.CONTROL),
            DetectedControlSymbol(Point2D(50f, 50f), 5f, ControlPointType.CONTROL),
            DetectedControlSymbol(Point2D(80f, 80f), 5f, ControlPointType.CONTROL),
        )
        val viewModel = MapProcessingViewModel(
            FakeProcessingEngine(symbolsResult = symbols, ocrResults = listOf(null, 65, 65)),
            Dispatchers.Unconfined,
        )

        onMainThread { viewModel.processImage(Uri.parse("content://test/review-codes")) }

        val points = viewModel.uiState.value.controlPoints
        assertEquals(listOf(0, 65, 65), points.map(ControlPoint::code))
        assertTrue(points.all(ControlPoint::needsReview))
    }

    @Test
    fun mapProcessing_manualAddAndEditClearReviewFlag() {
        val viewModel = MapProcessingViewModel(
            FakeProcessingEngine(ocrResults = listOf(null)),
            Dispatchers.Unconfined,
        )
        onMainThread { viewModel.processImage(Uri.parse("content://test/manual-review")) }
        val flagged = viewModel.uiState.value.controlPoints.single { it.type == ControlPointType.CONTROL }

        onMainThread {
            viewModel.updateControlPoint(flagged.copy(code = 72, points = 7))
            viewModel.addControlPoint(
                ControlPoint(code = 83, center = Point2D(70f, 70f), needsReview = true),
            )
        }

        val points = viewModel.uiState.value.controlPoints
        assertTrue(!points.single { it.id == flagged.id }.needsReview)
        assertTrue(!points.single { it.code == 83 }.needsReview)
    }

    @Test
    fun mapProcessing_keepsMapEditableWhenAutomaticPointDetectionFails() {
        val viewModel = MapProcessingViewModel(
            FakeProcessingEngine(symbolFailure = NullPointerException("detector unavailable")),
            Dispatchers.Unconfined,
        )

        onMainThread { viewModel.processImage(Uri.parse("content://test/detection-failure")) }

        val state = viewModel.uiState.value
        assertEquals(MapProcessingStage.COMPLETE, state.stage)
        assertEquals(null, state.error)
        assertTrue(state.controlPoints.isEmpty())
    }

    @Test
    fun mapProcessing_supportsUpdatingAddingAndRemovingControlPoints() {
        val viewModel = MapProcessingViewModel(FakeProcessingEngine(), Dispatchers.Unconfined)
        onMainThread { viewModel.processImage(Uri.parse("content://test/edit-points")) }
        val original = viewModel.uiState.value.controlPoints.first()
        val added = ControlPoint(code = 72, center = Point2D(25f, 30f))

        onMainThread {
            viewModel.updateControlPoint(original.copy(center = Point2D(20f, 20f)))
            viewModel.addControlPoint(added)
            viewModel.removeControlPoint(original.id)
        }

        val points = viewModel.uiState.value.controlPoints
        assertTrue(points.none { it.id == original.id })
        assertTrue(points.any { it.id == added.id && it.code == 72 })
    }

    @Test
    fun mapProcessing_collectsReferencesThenDetectsAndReplacesOnlyOnConfirm() {
        val sample = ColorCalibrationSample(140.0..160.0, 80.0..255.0, 60.0..255.0, 10f)
        val engine = FakeProcessingEngine(
            calibrationSample = sample,
            calibratedSymbols = listOf(
                DetectedControlSymbol(Point2D(75f, 20f), 10f, ControlPointType.CONTROL),
            ),
        )
        val viewModel = MapProcessingViewModel(engine, Dispatchers.Unconfined)
        onMainThread { viewModel.processImage(Uri.parse("content://test/calibration")) }
        val existingIds = viewModel.uiState.value.controlPoints.mapTo(mutableSetOf(), ControlPoint::id)
        val callsBeforeCalibration = engine.calls.toList()

        onMainThread {
            viewModel.startColorCalibration()
            assertTrue(viewModel.uiState.value.isCalibratingColor)
            viewModel.applyColorCalibrationSample(Point2D(75f, 20f))
            viewModel.applyColorCalibrationSample(Point2D(25f, 75f))
        }

        val previewState = viewModel.uiState.value
        assertEquals(MapProcessingStage.COMPLETE, previewState.stage)
        assertTrue(previewState.isCalibratingColor)
        assertEquals(existingIds, previewState.controlPoints.mapTo(mutableSetOf(), ControlPoint::id))
        assertEquals(
            listOf(Point2D(75f, 20f), Point2D(25f, 75f)),
            previewState.calibrationReferencePoints,
        )
        assertEquals(callsBeforeCalibration + listOf("sample", "sample"), engine.calls)

        onMainThread { viewModel.confirmColorCalibration() }

        val state = viewModel.uiState.value
        assertEquals(sample, state.colorCalibration)
        assertTrue(!state.isCalibratingColor)
        assertEquals(1, state.controlPoints.size)
        assertTrue(state.controlPoints.single().center == Point2D(75f, 20f))
        assertTrue(engine.calls.containsAll(listOf("sample", "calibratedSymbols", "isolate")))
    }

    @Test
    fun mapProcessing_movesAndClearsCalibrationReferences() {
        val sample = ColorCalibrationSample(140.0..160.0, 80.0..255.0, 60.0..255.0, 10f)
        val engine = FakeProcessingEngine(calibrationSample = sample)
        val viewModel = MapProcessingViewModel(engine, Dispatchers.Unconfined)
        onMainThread {
            viewModel.processImage(Uri.parse("content://test/calibration-move"))
            viewModel.startColorCalibration()
            viewModel.applyColorCalibrationSample(Point2D(20f, 20f))
            viewModel.moveColorCalibrationReference(0, Point2D(30f, 35f))
        }

        assertEquals(listOf(Point2D(30f, 35f)), viewModel.uiState.value.calibrationReferencePoints)
        assertTrue(viewModel.uiState.value.isSamplingColor)
        Thread.sleep(250)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(!viewModel.uiState.value.isSamplingColor)
        assertEquals(2, engine.calls.count { it == "sample" })

        onMainThread { viewModel.clearColorCalibrationReferences() }

        assertTrue(viewModel.uiState.value.calibrationReferencePoints.isEmpty())
        assertEquals(null, viewModel.uiState.value.pendingColorCalibration)
    }

    @Test
    fun mapProcessing_rejectsRunawayCalibrationBeforeOcr() {
        val sample = ColorCalibrationSample(140.0..160.0, 80.0..255.0, 60.0..255.0, 10f)
        val excessiveSymbols = List(81) { index ->
            DetectedControlSymbol(
                center = Point2D((index % 9 * 10).toFloat(), (index / 9 * 10).toFloat()),
                radius = 5f,
                type = ControlPointType.CONTROL,
            )
        }
        val engine = FakeProcessingEngine(
            calibrationSample = sample,
            calibratedSymbols = excessiveSymbols,
        )
        val viewModel = MapProcessingViewModel(engine, Dispatchers.Unconfined)
        onMainThread { viewModel.processImage(Uri.parse("content://test/runaway-calibration")) }
        val existingPoints = viewModel.uiState.value.controlPoints
        onMainThread {
            viewModel.startColorCalibration()
            viewModel.applyColorCalibrationSample(Point2D(50f, 50f))
        }
        val callsBeforeConfirm = engine.calls.size

        onMainThread { viewModel.confirmColorCalibration() }

        val state = viewModel.uiState.value
        assertEquals(existingPoints, state.controlPoints)
        assertTrue(state.isCalibratingColor)
        assertTrue(!state.isConfirmingColorCalibration)
        assertTrue(state.error.orEmpty().contains("matched too much map detail"))
        assertEquals(listOf("calibratedSymbols"), engine.calls.drop(callsBeforeConfirm))
    }

    @Test
    fun routing_buildsShortestRouteWithSegmentsAndTotals() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)
        val points = routePoints()

        onMainThread { viewModel.calculateRoute(points, 1f, RouteMode.SHORTEST) }

        val route = viewModel.optimizedRoute.value
        assertNotNull(route)
        assertEquals(listOf("start", "65", "finish"), route!!.path.map(ControlPoint::id))
        assertEquals(10f, route.totalDistanceMeters, 0.001f)
        assertEquals(6, route.totalScore)
        assertEquals(2, route.segments.size)
        assertEquals(10f, route.segments.last().accumulatedDistanceMeters, 0.001f)
        // Removing the collinear control does not reduce distance, so it is not a useful alternative.
        assertTrue(viewModel.uiState.value.alternativeRoutes.isEmpty())
    }

    @Test
    fun routing_bestScoreHonorsBudgetAndRejectsMissingEndpoints() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)

        onMainThread {
            viewModel.calculateRoute(routePoints(), 1f, RouteMode.BEST_SCORE, budgetMeters = 10f)
        }
        assertEquals(6, viewModel.optimizedRoute.value?.totalScore)
        assertTrue(viewModel.uiState.value.alternativeRoutes.isEmpty())

        onMainThread {
            viewModel.calculateRoute(
                detectedPoints = routePoints().filterNot { it.type == ControlPointType.FINISH },
                pixelsPerMeter = 1f,
                mode = RouteMode.SHORTEST,
            )
        }
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("finish"))
    }

    @Test
    fun importedMap_opensAsUnsavedWorkingCopy() {
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        val imported = SavedMap(
            id = "exported-id",
            name = "Imported course",
            bitmap = bitmap,
            pixelsPerMeter = 2f,
            points = emptyList(),
            route = null,
            alternativeRoutes = emptyList(),
            routeMetadata = emptyMap(),
            routeRestrictions = emptyList(),
            selectedRoutePointIds = emptyList(),
            selectedRouteId = null,
            routeMode = "SHORTEST",
            routeBudgetMeters = null,
            routeTargetScore = null,
            lineStart = null,
            lineEnd = null,
            lineDistanceMeters = null,
            rotationQuarterTurns = 0,
        )
        val viewModel = MapProcessingViewModel(FakeProcessingEngine(), Dispatchers.Unconfined)

        onMainThread { viewModel.openImportedMap(imported) }

        val state = viewModel.uiState.value
        assertEquals(MapProcessingStage.COMPLETE, state.stage)
        assertEquals(null, state.savedMapId)
        assertEquals("Imported course", state.savedMapName)
        assertTrue(state.savedContentDirty)
    }

    @Test
    fun routing_newGenerationAppendsAndSelectsAUniquelyNamedRoute() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)

        onMainThread {
            viewModel.calculateRoute(routePoints(), 1f, RouteMode.SHORTEST)
            viewModel.calculateRoute(routePoints(), 1f, RouteMode.SHORTEST)
        }

        val state = viewModel.uiState.value
        assertEquals(1, state.alternativeRoutes.size)
        assertEquals("Route", state.routeMetadata[state.route?.id]?.name)
        val appended = state.alternativeRoutes.single()
        assertEquals("Route (2)", state.routeMetadata[appended.id]?.name)
        assertEquals(appended.id, state.selectedRouteId)
    }

    @Test
    fun routing_managementKeepsStarAndDisplayAndMovesSelectionFromHiddenRoute() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)
        onMainThread {
            viewModel.calculateRoute(routePoints(), 1f, RouteMode.SHORTEST)
            viewModel.calculateRoute(routePoints(), 1f, RouteMode.SHORTEST)
        }
        val before = viewModel.uiState.value
        val routes = listOfNotNull(before.route) + before.alternativeRoutes
        val selected = routes.single { it.id == before.selectedRouteId }
        val visible = routes.first { it.id != selected.id }

        onMainThread {
            viewModel.manageRoutes(
                RouteManagementAction.Apply(
                    orderedRoutes = routes,
                    metadata = routes.associate { route ->
                        route.id to if (route.id == selected.id) {
                            RouteMetadata(isHidden = true)
                        } else {
                            RouteMetadata(isStarred = true, isDisplayed = true)
                        }
                    },
                ),
            )
        }

        val after = viewModel.uiState.value
        assertEquals(visible.id, after.selectedRouteId)
        assertTrue(after.routeMetadata.getValue(visible.id).isStarred)
        assertTrue(after.routeMetadata.getValue(visible.id).isDisplayed)
    }

    @Test
    fun routing_editPathPreservesRouteIdentityAndRebuildsStatistics() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)
        val points = routePoints()
        onMainThread { viewModel.calculateRoute(points, 1f, RouteMode.SHORTEST) }
        val before = requireNotNull(viewModel.uiState.value.route)

        onMainThread {
            viewModel.manageRoutes(
                RouteManagementAction.UpdatePath(
                    routeId = before.id,
                    path = listOf(points.first(), points.last()),
                    pixelsPerMeter = 1f,
                ),
            )
        }

        val edited = requireNotNull(viewModel.uiState.value.route)
        assertEquals(before.id, edited.id)
        assertEquals(listOf("start", "finish"), edited.path.map(ControlPoint::id))
        assertEquals(10f, edited.totalDistanceMeters)
        assertEquals(0, edited.totalScore)
    }

    @Test
    fun routing_restrictionsDoNotChangeExistingRouteAndApplyToNextGeneration() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)
        val points = routePoints()
        onMainThread { viewModel.calculateRoute(points, 1f, RouteMode.SHORTEST) }
        val existing = requireNotNull(viewModel.uiState.value.route)
        val control = points.single { it.type == ControlPointType.CONTROL }

        onMainThread {
            viewModel.manageRouteRestrictions(
                RouteRestrictionAction.Add(
                    RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, control.id),
                ),
            )
        }
        assertTrue(existing.path.any { it.id == control.id })

        onMainThread {
            viewModel.calculateRoute(points, 1f, RouteMode.BEST_SCORE, budgetMeters = 10f)
        }
        val state = viewModel.uiState.value
        val generated = (listOfNotNull(state.route) + state.alternativeRoutes)
            .single { it.id == state.selectedRouteId }
        assertTrue(generated.path.none { it.id == control.id })
        assertTrue(existing.path.any { it.id == control.id })
    }

    @Test
    fun routing_starredRestrictionsAreProtectedFromDeletion() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)
        val restriction = RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, "65")
        onMainThread {
            viewModel.manageRouteRestrictions(RouteRestrictionAction.Add(restriction))
            viewModel.manageRouteRestrictions(RouteRestrictionAction.ToggleStar(restriction.id))
            viewModel.manageRouteRestrictions(RouteRestrictionAction.Delete(restriction.id))
            viewModel.manageRouteRestrictions(RouteRestrictionAction.DeleteAllUnstarred)
        }
        assertTrue(viewModel.uiState.value.routeRestrictions.single().isStarred)

        onMainThread {
            viewModel.manageRouteRestrictions(RouteRestrictionAction.ToggleStar(restriction.id))
            viewModel.manageRouteRestrictions(RouteRestrictionAction.DeleteAllUnstarred)
        }
        assertTrue(viewModel.uiState.value.routeRestrictions.isEmpty())
    }

    @Test
    fun routing_alternativesKeepPrefixThroughSelectedSplitPoint() {
        val viewModel = RoutingViewModel(Dispatchers.Unconfined)
        val points = listOf(
            ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START),
            ControlPoint("61", 61, 6, Point2D(2f, 1f), ControlPointType.CONTROL),
            ControlPoint("72", 72, 7, Point2D(4f, -1f), ControlPointType.CONTROL),
            ControlPoint("83", 83, 8, Point2D(5f, 3f), ControlPointType.CONTROL),
            ControlPoint("finish", 0, 0, Point2D(8f, 0f), ControlPointType.FINISH),
        )
        onMainThread { viewModel.calculateRoute(points, 1f, RouteMode.SHORTEST) }
        val source = requireNotNull(viewModel.uiState.value.route)

        onMainThread {
            viewModel.generateAlternativeRoutes(
                points,
                1f,
                AlternativeRouteCriteria(
                    count = 5,
                    sourceRouteId = source.id,
                    fixedPrefixPointCount = 2,
                ),
            )
        }

        val generated = viewModel.uiState.value.alternativeRoutes.filter {
            viewModel.uiState.value.routeMetadata[it.id]?.isAlternative == true
        }
        assertTrue(generated.isNotEmpty())
        assertTrue(generated.all { it.path.take(2).map(ControlPoint::id) == source.path.take(2).map(ControlPoint::id) })
    }

    private fun routePoints() = listOf(
        ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START),
        ControlPoint("65", 65, 6, Point2D(3f, 4f), ControlPointType.CONTROL),
        ControlPoint("finish", 0, 0, Point2D(6f, 8f), ControlPointType.FINISH),
    )

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class FakeProcessingEngine(
        private val boundaryResult: MapBoundary? = MapBoundary(
            Point2D(0f, 0f),
            Point2D(99f, 0f),
            Point2D(99f, 99f),
            Point2D(0f, 99f),
        ),
        private val ocrFailure: Throwable? = null,
        private val symbolFailure: Throwable? = null,
        private val calibrationSample: ColorCalibrationSample? = null,
        private val calibratedSymbols: List<DetectedControlSymbol> = emptyList(),
        private val symbolsResult: List<DetectedControlSymbol>? = null,
        private val ocrResults: List<Int?> = listOf(65),
    ) : MapProcessingEngine {
        val calls = mutableListOf<String>()
        private val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        private var ocrIndex = 0

        override fun decode(uri: Uri): Bitmap = bitmap.also { calls += "decode" }

        override fun detectBoundary(bitmap: Bitmap): MapBoundary? =
            boundaryResult.also { calls += "boundary" }

        override fun warpPerspective(bitmap: Bitmap, boundary: MapBoundary): Bitmap =
            bitmap.also { calls += "warp" }

        override fun detectControlSymbols(bitmap: Bitmap): List<DetectedControlSymbol> {
            calls += "symbols"
            symbolFailure?.let { throw it }
            return symbolsResult ?: listOf(
                DetectedControlSymbol(Point2D(10f, 10f), 5f, ControlPointType.START),
                DetectedControlSymbol(Point2D(50f, 50f), 5f, ControlPointType.CONTROL),
                DetectedControlSymbol(Point2D(90f, 90f), 5f, ControlPointType.FINISH),
            )
        }

        override fun cropRegion(bitmap: Bitmap, symbol: DetectedControlSymbol): Bitmap =
            bitmap.also { calls += "crop" }

        override suspend fun extractControlNumber(bitmap: Bitmap): Int? {
            calls += "ocr"
            ocrFailure?.let { throw it }
            return ocrResults.getOrElse(ocrIndex++) { ocrResults.lastOrNull() }
        }

        override fun detectControlSymbols(
            bitmap: Bitmap,
            colorCalibration: ColorCalibrationSample?,
        ): List<DetectedControlSymbol> = if (colorCalibration == null) {
            detectControlSymbols(bitmap)
        } else {
            calls += "calibratedSymbols"
            calibratedSymbols
        }

        override fun sampleControlPointColor(
            bitmap: Bitmap,
            tapPoint: Point2D,
        ): ColorCalibrationSample? = calibrationSample.also { calls += "sample" }

        override fun isolateInkColor(
            bitmap: Bitmap,
            colorCalibration: ColorCalibrationSample?,
        ): Bitmap = bitmap.also { if (colorCalibration != null) calls += "isolate" }
    }
}
