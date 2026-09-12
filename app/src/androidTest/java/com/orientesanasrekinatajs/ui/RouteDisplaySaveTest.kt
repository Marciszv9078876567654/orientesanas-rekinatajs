package com.orientesanasrekinatajs.ui

import android.graphics.Bitmap
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

class RouteDisplaySaveTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun viewChoicesStayCleanAndAreIncludedInExplicitSave() {
        val start = ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START)
        val finish = ControlPoint("finish", 0, 0, Point2D(10f, 10f), ControlPointType.FINISH)
        val route = OptimizedRoute(listOf(start, finish), 14f, 0, emptyList(), "primary")
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
        val savedTitle = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.route_saved_title, "Test map")
        compose.onNodeWithText(label(R.string.show_route_details)).performClick()
        for (id in listOf(R.string.points_per_kilometer, R.string.use_relative_values, R.string.show_point_numbering)) {
            compose.onNodeWithContentDescription(label(id)).assertIsOn().performClick().assertIsOff()
            compose.onNodeWithText(savedTitle).assertExists()
        }
        compose.onNodeWithText("Alternative").performClick()
        compose.onNodeWithText(savedTitle).assertExists()
        compose.onNodeWithContentDescription(label(R.string.map_actions)).performClick()
        compose.onNodeWithText(label(R.string.save_map)).performClick()
        compose.onNodeWithText(label(R.string.save)).performClick()
        compose.runOnIdle {
            assertEquals(alternative.id, saved?.selectedRoute?.id)
            assertEquals(false, saved?.detailsPointsPerKilometer)
            assertEquals(false, saved?.detailsRelativeValues)
            assertEquals(false, saved?.detailsPointNumbering)
        }
    }
}
