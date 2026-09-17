package com.orientesanasrekinatajs.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7UiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun imageSource_exposesPickerAndCameraActions() {
        var composed = false
        composeRule.setContent {
            ImageSourceScreen(onImageSelected = {}, onOpenSettings = {})
            SideEffect { composed = true }
        }

        composeRule.runOnIdle { assertTrue(composed) }
    }

    @Test
    fun cornerDrag_mapsCanvasPositionToImageCoordinates() {
        val updated = updateBoundaryCorner(
            boundary = boundary(),
            cornerIndex = 0,
            dragPosition = Offset(50f, 50f),
            canvasSize = IntSize(200, 200),
            imageWidth = 100,
            imageHeight = 100,
        )

        assertEquals(25f, updated.topLeft.x, 0.01f)
        assertEquals(25f, updated.topLeft.y, 0.01f)
    }

    @Test
    fun pinchZoom_keepsTheGestureCentroidStationary() {
        val (zoom, pan) = updatedViewportForGesture(
            zoomChange = 2f,
            panChange = Offset.Zero,
            centroid = Offset(150f, 100f),
            viewportCenter = Offset(100f, 100f),
            currentZoom = 1f,
            currentPan = Offset.Zero,
        )

        assertEquals(2f, zoom, 0.001f)
        assertEquals(-50f, pan.x, 0.001f)
        assertEquals(0f, pan.y, 0.001f)
    }

    @Test
    fun defaultScale_acceptsPanGestures() {
        val (zoom, pan) = updatedViewportForGesture(
            zoomChange = 1f,
            panChange = Offset(24f, -12f),
            centroid = Offset(100f, 100f),
            viewportCenter = Offset(100f, 100f),
            currentZoom = 1f,
            currentPan = Offset.Zero,
        )

        assertEquals(1f, zoom, 0.001f)
        assertEquals(24f, pan.x, 0.001f)
        assertEquals(-12f, pan.y, 0.001f)
    }

    @Test
    fun distanceCalibrationCanvas_rendersZoomableTwoPointLine() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        var composed = false

        composeRule.setContent {
            DistanceCalibrationCanvas(
                bitmap = bitmap,
                start = null,
                end = null,
                onLineChange = { _, _ -> },
                modifier = Modifier.size(200.dp),
            )
            SideEffect { composed = true }
        }

        composeRule.runOnIdle { assertTrue(composed) }
    }

    @Test
    fun routeCanvasAndStepTable_renderRouteData() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val start = point("start", 0, 10f, ControlPointType.START)
        val control = point("control", 65, 50f, ControlPointType.CONTROL)
        val finish = point("finish", 0, 90f, ControlPointType.FINISH)
        val route = OptimizedRoute(
            path = listOf(start, control, finish),
            totalDistanceMeters = 200f,
            totalScore = 6,
            segments = listOf(
                RouteSegment(start, control, 80f, 80f, 6),
                RouteSegment(control, finish, 120f, 200f, 6),
            ),
        )

        var composed = false
        composeRule.setContent {
            Column {
                RouteRenderingCanvas(
                    bitmap = bitmap,
                    route = route.path,
                    modifier = Modifier.size(200.dp),
                )
                RouteStepTable(route = route, modifier = Modifier.size(300.dp))
            }
            SideEffect { composed = true }
        }

        composeRule.runOnIdle { assertTrue(composed) }
        assertTableDistances(route)
    }

    @Test
    fun stepTable_acceptsCombinedStartFinishAtBothEnds() {
        val combined = point("combined", 0, 10f, ControlPointType.START_FINISH)
        val control = point("control", 65, 50f, ControlPointType.CONTROL)
        val route = OptimizedRoute(
            path = listOf(combined, control, combined),
            totalDistanceMeters = 160f,
            totalScore = 6,
            segments = listOf(
                RouteSegment(combined, control, 80f, 80f, 6),
                RouteSegment(control, combined, 80f, 160f, 6),
            ),
        )
        var composed = false

        composeRule.setContent {
            RouteStepTable(route = route, modifier = Modifier.size(360.dp))
            SideEffect { composed = true }
        }

        composeRule.runOnIdle { assertTrue(composed) }
        assertTableDistances(route)
    }

    @Test
    fun stepTable_diagonalDragScrollsBothAxesBeforeRelease() {
        val path = (0 until 30).map { index ->
            point("point:$index", 100 + index, index.toFloat(), ControlPointType.CONTROL)
        }
        val route = OptimizedRoute(
            path = path,
            totalDistanceMeters = 2900f,
            totalScore = 0,
            segments = path.zipWithNext().mapIndexed { index, (from, to) ->
                RouteSegment(from, to, 100f, (index + 1) * 100f, 0)
            },
        )
        composeRule.setContent {
            RouteStepTable(route = route, modifier = Modifier.size(300.dp))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val header = composeRule.onNodeWithText(context.getString(R.string.distance_header))
        val initialHeader = header.fetchSemanticsNode().positionInRoot
        val body = composeRule.onNodeWithTag("routeStepTableBody")
        fun verticalPosition() = body.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        val initialVertical = verticalPosition()
        val table = composeRule.onNodeWithTag("routeStepTable")

        table.performTouchInput {
            down(Offset(width * 0.8f, height * 0.8f))
            moveBy(Offset(-60f, -80f))
            moveBy(Offset(-60f, -80f))
        }
        // Assert while the finger is still down, so a fling cannot hide an axis lock.
        val draggedHeader = header.fetchSemanticsNode().positionInRoot
        assertTrue(draggedHeader.x < initialHeader.x)
        assertEquals(initialHeader.y, draggedHeader.y, 0.5f)
        assertTrue(verticalPosition() > initialVertical)

        val draggedVertical = verticalPosition()
        table.performTouchInput { moveBy(Offset(40f, 50f)) }
        assertTrue(header.fetchSemanticsNode().positionInRoot.x > draggedHeader.x)
        assertTrue(verticalPosition() < draggedVertical)
        table.performTouchInput { up() }
    }

    @Test
    fun stepTable_condensesSmallOverflowButScrollsLargerOverflow() {
        val start = point("start", 0, 0f, ControlPointType.START)
        val finish = point("finish", 0, 10f, ControlPointType.FINISH)
        val route = OptimizedRoute(
            listOf(start, finish), 100f, 0,
            listOf(RouteSegment(start, finish, 100f, 100f, 0)),
        )
        val tableWidth = mutableStateOf(300.dp)
        composeRule.setContent {
            RouteStepTable(route, Modifier.requiredWidth(tableWidth.value).height(300.dp))
        }
        val header = composeRule.onNodeWithTag("routeStepTableHeader")
        val naturalSize = header.fetchSemanticsNode().size
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        fun horizontalRange() = composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange),
        ).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]

        composeRule.runOnIdle { tableWidth.value = (naturalSize.width / density * 0.95f).dp }
        composeRule.waitForIdle()
        assertEquals("Small overflow should fit without scrolling", 0f, horizontalRange().maxValue(), 1f)
        assertEquals("Text keeps its original height", naturalSize.height, header.fetchSemanticsNode().size.height)
        val headerX = header.fetchSemanticsNode().positionInRoot.x
        composeRule.onNodeWithTag("routeStepTable").performTouchInput {
            swipe(Offset(width * 0.8f, 10f), Offset(width * 0.2f, 10f))
        }
        assertEquals(headerX, header.fetchSemanticsNode().positionInRoot.x, 1f)

        composeRule.runOnIdle { tableWidth.value = (naturalSize.width / density * 0.8f).dp }
        composeRule.waitForIdle()
        assertTrue("Large overflow should remain scrollable", horizontalRange().maxValue() > 0f)
        composeRule.onNodeWithTag("routeStepTable").performTouchInput {
            swipe(Offset(width * 0.8f, 10f), Offset(width * 0.2f, 10f))
        }
        assertTrue(horizontalRange().value() > 0f)
    }

    private fun assertTableDistances(route: OptimizedRoute) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val headers = composeRule.onNodeWithTag("routeStepTableHeader", useUnmergedTree = true)
            .onChildren().fetchSemanticsNodes()
        val expectedGap = 16f * context.resources.displayMetrics.density
        headers.zipWithNext().forEach { (left, right) ->
            assertEquals(
                expectedGap,
                right.positionInRoot.x - left.positionInRoot.x - left.size.width,
                1f,
            )
        }
        assertTrue("Score should be narrower than distance total", headers[4].size.width < headers[3].size.width)
        val inTable = hasAnyAncestor(hasTestTag("routeStepTable"))
        composeRule.onNode(
            hasText(context.getString(R.string.distance_total_header)) and inTable,
            useUnmergedTree = true,
        ).assertExists()

        val displayedTotals = route.path.indices.map { index ->
            val cells = composeRule.onNodeWithTag(
                "routeStepRow:$index",
                useUnmergedTree = true,
            ).onChildren()
            cells.assertCountEquals(6)
            cells.fetchSemanticsNodes().forEachIndexed { column, cell ->
                assertEquals(headers[column].size.width, cell.size.width)
                assertEquals(headers[column].positionInRoot.x, cell.positionInRoot.x, 1f)
            }
            cells[2].assertTextEquals(context.getString(
                R.string.distance_meters_format,
                route.segments.getOrNull(index - 1)?.distanceMeters ?: 0f,
            ))
            cells[3].assertTextEquals(context.getString(
                R.string.distance_meters_format,
                route.segments.getOrNull(index - 1)?.accumulatedDistanceMeters ?: 0f,
            ))
            cells[3].fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        }
        assertEquals(context.getString(R.string.distance_meters_format, 0f), displayedTotals.first())
        assertEquals(
            context.getString(R.string.distance_meters_format, route.totalDistanceMeters),
            displayedTotals.last(),
        )
        val numbers = displayedTotals.map { it.substringBefore(" ").toFloat() }
        assertTrue(numbers.zipWithNext().all { (previous, next) -> next >= previous })

        // Even at the 300/360 dp test widths, the last column can be brought into view.
        composeRule.onNodeWithTag("routeStepTable").performTouchInput { swipeLeft() }
        composeRule.onNode(
            hasText(context.getString(R.string.score_total_header)) and inTable,
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    private fun boundary() = MapBoundary(
        topLeft = Point2D(10f, 10f),
        topRight = Point2D(90f, 10f),
        bottomRight = Point2D(90f, 90f),
        bottomLeft = Point2D(10f, 90f),
    )

    private fun point(
        id: String,
        code: Int,
        coordinate: Float,
        type: ControlPointType,
    ) = ControlPoint(
        id = id,
        code = code,
        center = Point2D(coordinate, coordinate),
        type = type,
    )
}
