package com.orientesanasrekinatajs.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.*
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import org.junit.Rule
import org.junit.Test

class RouteRestrictionDialogTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun mandatoryConnectionLimitExplainsDisabledAdd() {
        val points = (31..34).map { ControlPoint(id = "$it", code = it, center = Point2D(it.toFloat(), 0f)) }
        compose.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                AddRouteRestrictionDialog(points, listOf(
                    RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "31", "33"),
                    RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "31", "34"),
                ), {}, {})
            }
        }
        compose.onNodeWithText(label(R.string.blacklist_control)).performClick()
        compose.onNodeWithText(label(R.string.mandatory_connection)).performClick()
        compose.onNodeWithText(label(R.string.add)).assertIsNotEnabled()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.mandatory_connection_limit, "31", 2)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.mandatory_connection)).performClick()
        compose.onNodeWithText(label(R.string.blacklist_connection)).performClick()
        compose.onNodeWithText(label(R.string.add)).assertIsEnabled()
    }
}
