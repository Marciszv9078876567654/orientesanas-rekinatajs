package com.orientesanasrekinatajs.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.imageprocessing.DetectedControlSymbol
import com.orientesanasrekinatajs.ui.processing.MapProcessingEngine
import com.orientesanasrekinatajs.ui.processing.MapProcessingStage
import com.orientesanasrekinatajs.ui.processing.MapProcessingViewModel
import com.orientesanasrekinatajs.ui.routing.RouteMode
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction
import com.orientesanasrekinatajs.ui.routing.RoutingViewModel
import com.orientesanasrekinatajs.domain.model.RouteMetadata
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
    ) : MapProcessingEngine {
        val calls = mutableListOf<String>()
        private val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        override fun decode(uri: Uri): Bitmap = bitmap.also { calls += "decode" }

        override fun detectBoundary(bitmap: Bitmap): MapBoundary? =
            boundaryResult.also { calls += "boundary" }

        override fun warpPerspective(bitmap: Bitmap, boundary: MapBoundary): Bitmap =
            bitmap.also { calls += "warp" }

        override fun detectControlSymbols(bitmap: Bitmap): List<DetectedControlSymbol> {
            calls += "symbols"
            return listOf(
                DetectedControlSymbol(Point2D(10f, 10f), 5f, ControlPointType.START),
                DetectedControlSymbol(Point2D(50f, 50f), 5f, ControlPointType.CONTROL),
                DetectedControlSymbol(Point2D(90f, 90f), 5f, ControlPointType.FINISH),
            )
        }

        override fun cropRegion(bitmap: Bitmap, symbol: DetectedControlSymbol): Bitmap =
            bitmap.also { calls += "crop" }

        override suspend fun extractControlNumber(bitmap: Bitmap): Int? = 65.also { calls += "ocr" }
    }
}
