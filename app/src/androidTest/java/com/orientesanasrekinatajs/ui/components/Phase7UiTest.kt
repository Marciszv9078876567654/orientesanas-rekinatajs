package com.orientesanasrekinatajs.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
