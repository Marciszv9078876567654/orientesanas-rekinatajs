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

    private fun show(rules: List<RouteRestriction>, selectedTypeLabel: Int = R.string.blacklist_control) {
        val points = (31..34).map { ControlPoint(id = "$it", code = it, center = Point2D(it.toFloat(), 0f)) }
        compose.setContent {
            OrienteeringAppTheme(UserPreferences()) { AddRouteRestrictionDialog(points, rules, {}, {}) }
        }
        if (selectedTypeLabel != R.string.blacklist_control) {
            compose.onNodeWithText(label(R.string.blacklist_control)).performClick()
            compose.onNodeWithText(label(selectedTypeLabel)).performClick()
        }
    }

    private fun assertPointConflict(code: String = "31") {
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.restriction_point_contradiction, code)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.add)).assertIsNotEnabled()
    }

    @Test fun mandatoryControlCannotUseBlacklistedPoint() {
        show(listOf(RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, "31")), R.string.mandatory_control)
        assertPointConflict()
    }

    @Test fun blacklistedControlCannotUseMandatoryPoint() {
        show(listOf(RouteRestriction(RouteRestrictionType.MANDATORY_CONTROL, "31")))
        assertPointConflict()
    }

    @Test fun blacklistedControlCannotUseMandatoryConnectionEndpoint() {
        show(listOf(RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "34", "31")))
        assertPointConflict()
    }

    @Test fun mandatoryConnectionCannotUseBlacklistedFirstPoint() {
        show(listOf(RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, "31")), R.string.mandatory_connection)
        assertPointConflict()
    }

    @Test fun mandatoryConnectionCannotUseBlacklistedSecondPoint() {
        show(listOf(RouteRestriction(RouteRestrictionType.BLACKLIST_CONTROL, "32")), R.string.mandatory_connection)
        assertPointConflict("32")
    }

    private fun assertConnectionConflict() {
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.restriction_connection_contradiction, "31", "32")).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.add)).assertIsNotEnabled()
    }

    @Test fun blacklistedConnectionCannotUseReversedMandatoryPair() {
        show(listOf(RouteRestriction(RouteRestrictionType.MANDATORY_CONNECTION, "32", "31")), R.string.blacklist_connection)
        assertConnectionConflict()
    }

    @Test fun mandatoryConnectionCannotUseReversedBlacklistedPair() {
        show(listOf(RouteRestriction(RouteRestrictionType.BLACKLIST_CONNECTION, "32", "31")), R.string.mandatory_connection)
        assertConnectionConflict()
    }

    @Test fun changingToAnUnrelatedPairClearsWarningAndEnablesAdd() {
        show(listOf(RouteRestriction(RouteRestrictionType.BLACKLIST_CONNECTION, "32", "31")), R.string.mandatory_connection)
        assertConnectionConflict()
        compose.onNodeWithText("32").performClick()
        compose.onNodeWithText("34").performClick()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.restriction_connection_contradiction, "31", "32")).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.add)).assertIsEnabled()
    }

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
