package com.orientesanasrekinatajs.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.domain.model.*
import com.orientesanasrekinatajs.ui.processing.MapProcessingUiState
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction
import com.orientesanasrekinatajs.ui.routing.RoutingUiState
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MapPanelIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun managePanelLeavesMapInteractiveAndUnchangedEditorCancelsDirectly() {
        val start = ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START)
        val finish = ControlPoint("finish", 0, 0, Point2D(10f, 10f), ControlPointType.FINISH)
        val controls = listOf(
            ControlPoint("c1", 31, 1, Point2D(3f, 3f), ControlPointType.CONTROL),
            ControlPoint("c2", 32, 1, Point2D(6f, 6f), ControlPointType.CONTROL),
        )
        val route = OptimizedRoute(listOf(start) + controls + finish, 14f, 2, emptyList(), "primary")
        val alternative = route.copy(id = "alternative")
        val processing = MapProcessingUiState(
            rectifiedBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888),
            controlPoints = route.path, savedMapId = "saved", savedMapName = "Test map",
            savedPixelsPerMeter = 1f,
            savedDetailsPointsPerKilometer = true,
            savedDetailsRelativeValues = true,
            savedDetailsPointNumbering = true,
        )
        var saved: SavedMapDraft? = null
        compose.setContent {
            var routing by remember { mutableStateOf(RoutingUiState(
                route = route, alternativeRoutes = listOf(alternative), selectedRouteId = route.id,
                routeMetadata = mapOf(route.id to RouteMetadata(name = "Primary"),
                    alternative.id to RouteMetadata(name = "Alternative")),
            )) }
            OrienteeringAppTheme(UserPreferences()) {
                MapFlowScreen(
                    processingState = processing, routingState = routing,
                    defaultBudgetMeters = 1000f, showUsageTips = false,
                    calibrationUsageTipsEnabled = false, onUsageTipDismissed = {},
                    onApplyEditedBoundary = { _, _, _, _ -> }, onUpdateControlPoint = {},
                    onAddControlPoint = {}, onRemoveControlPoint = {}, onClearControlPoints = {},
                    onStartColorCalibration = {}, onApplyColorCalibrationSample = {},
                    onMoveColorCalibrationReference = { _, _ -> }, onClearColorCalibrationReferences = {},
                    onConfirmColorCalibration = {}, onCancelColorCalibration = {},
                    onDismissProcessingError = {}, onDismissReviewSummary = {}, onRestoreMapState = {},
                    onInvalidateRoute = {}, onCalculateRoute = { _, _, _, _ -> },
                    onGenerateAlternativeRoutes = { _, _, _ -> },
                    onManageRoutes = { action ->
                        if (action is RouteManagementAction.Select) routing = routing.copy(selectedRouteId = action.routeId)
                    },
                    onManageRouteRestrictions = {}, onExportMap = { _, _, _ -> },
                    onExportPdf = { _, _ -> }, onSaveMap = { draft, _ -> saved = draft },
                    onRenameSavedMap = { _, _, _ -> }, onDeleteSavedMap = { _, _ -> },
                    isSavingMap = false, isTransferringMap = false, rotation = 0,
                    rotationOffsetDegrees = 0f, rotationGesturesEnabled = false,
                    onRotationGesture = {}, onSnapRotation = {}, onRotationChange = {}, onBackHome = {},
                )
            }
        }
        fun openRouteMenu() = compose.onNodeWithContentDescription(label(R.string.route_actions)).performClick()
        openRouteMenu()
        compose.onNodeWithText(label(R.string.edit_route)).performClick()
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.onNodeWithText(label(R.string.cancel_route_edit_title)).assertDoesNotExist()
        compose.onNodeWithTag("route_editor_headers").assertDoesNotExist()

        openRouteMenu()
        compose.onNodeWithText(label(R.string.edit_route)).performClick()
        compose.onNode(hasContentDescription(label(R.string.move_point_down)) and
            hasAnyAncestor(hasTestTag("route_point_c1"))).performClick()
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.onNodeWithText(label(R.string.cancel_route_edit_title)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.confirm)).performClick()

        openRouteMenu()
        compose.onNodeWithText(label(R.string.manage_routes)).performClick()
        compose.onNodeWithTag("manage_routes_panel").assertIsDisplayed()
        val initial = compose.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
        val screen = compose.onNodeWithTag("route_screen").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.bottom, initial.bottom, 1f)
        compose.onNode(hasContentDescription(label(R.string.route_actions)) and
            hasAnyAncestor(hasTestTag("managed_route_alternative"))).performClick()
        compose.onNodeWithText(label(R.string.set_active_route)).performClick()
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.route_saved_title, "Test map")
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithTag("routeCanvas").performTouchInput { click(Offset(20f, 20f)) }
        compose.onNodeWithTag("managed_routes_list").assertDoesNotExist()
        compose.onNodeWithTag("manage_routes_title").performTouchInput {
            down(center)
            moveBy(Offset(0f, -1500f), delayMillis = 300)
            up()
        }
        compose.onNodeWithTag("managed_routes_list").assertExists()
        val expanded = compose.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
        assertEquals(screen, expanded)
        compose.onNodeWithTag("manage_routes_title").performTouchInput {
            down(center)
            moveBy(Offset(0f, screen.height), delayMillis = 300)
            up()
        }
        compose.onNodeWithTag("managed_routes_list").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.save)).performClick()
        compose.onNodeWithContentDescription(label(R.string.map_actions)).performClick()
        compose.onNodeWithText(label(R.string.save_map)).performClick()
        compose.onNodeWithText(label(R.string.save)).performClick()
        compose.runOnIdle { assertEquals(alternative.id, saved?.selectedRoute?.id) }
    }
}
