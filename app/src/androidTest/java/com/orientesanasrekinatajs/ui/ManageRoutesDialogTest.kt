package com.orientesanasrekinatajs.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.UserPreferences
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import org.junit.Rule
import org.junit.Test

class ManageRoutesDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tappingDialogBackgroundClearsRouteNameFocus() {
        val start = ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START)
        val finish = ControlPoint("finish", 0, 0, Point2D(10f, 10f), ControlPointType.FINISH)
        val route = OptimizedRoute(listOf(start, finish), 100f, 0, emptyList())

        composeRule.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                ManageRoutesDialog(
                    primaryRoute = route,
                    alternativeRoutes = emptyList(),
                    primaryRouteIndex = 0,
                    routeMetadata = emptyMap(),
                    onAction = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("managed_route_name_0")
            .performClick()
            .assertIsFocused()

        composeRule.onNodeWithTag("manage_routes_dialog_background")
            .performTouchInput { click(Offset(10f, 10f)) }

        composeRule.onNodeWithTag("managed_route_name_0").assertIsNotFocused()
    }
}
