package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.layout.ime
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Volatile private var keyboardInset = 0

    private fun showRoutes(count: Int = 3) {
        val routes = (0 until count).map { OptimizedRoute(emptyList(), 100f, 0, emptyList(), id = "r$it") }
        composeRule.setContent {
            val inset = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
            androidx.compose.runtime.SideEffect { keyboardInset = inset }
            OrienteeringAppTheme(UserPreferences()) {
                ManageRoutesDialog(routes[0], routes.drop(1), 0,
                    routes.associate { it.id to RouteMetadata(name = "Route ${it.id}") }, {}, {})
            }
        }
    }

    @Test fun panelSpansScreenAndStarTracksMenuSelection() {
        showRoutes()
        val bounds = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
        val screenWidth = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.widthPixels
        assertEquals(0f, bounds.left, 2f)
        assertEquals(screenWidth.toFloat(), bounds.width, 2f)
        fun menu() = composeRule.onNode(hasContentDescription(label(R.string.route_actions)) and
            hasAnyAncestor(hasTestTag("managed_route_r0"))).performClick()
        composeRule.onNodeWithTag("managed_route_star_r0", useUnmergedTree = true).assertDoesNotExist()
        menu()
        composeRule.onNodeWithText(label(R.string.star_route)).performClick()
        composeRule.onNodeWithTag("managed_route_star_r0", useUnmergedTree = true).assertIsDisplayed()
        menu()
        composeRule.onNodeWithText(label(R.string.unstar_route)).performClick()
        composeRule.onNodeWithTag("managed_route_star_r0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun draggedRouteStaysBetweenTitleAndDeleteButton() {
        showRoutes()
        val list = composeRule.onNodeWithTag("managed_routes_list")
        val title = composeRule.onNodeWithTag("manage_routes_title").fetchSemanticsNode().boundsInRoot
        val delete = composeRule.onNodeWithTag("managed_routes_delete_unstarred").fetchSemanticsNode().boundsInRoot
        val handle = composeRule.onNodeWithTag("managed_route_drag_r1")
        val from = handle.fetchSemanticsNode().boundsInRoot.center.y
        composeRule.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, title.center.y - from), delayMillis = 100)
        }
        composeRule.mainClock.advanceTimeBy(64)
        fun assertInsideList() {
            val viewport = list.fetchSemanticsNode().boundsInRoot
            val preview = composeRule.onNodeWithTag("managed_route_drag_preview").fetchSemanticsNode().boundsInRoot
            assertTrue("Preview $preview must stay in list $viewport",
                preview.top >= viewport.top - 1f && preview.bottom <= viewport.bottom + 1f)
            assertTrue("Preview $preview must stay below title $title and delete $delete",
                preview.top >= title.bottom && preview.top >= delete.bottom)
        }
        assertInsideList()
        val belowList = list.fetchSemanticsNode().boundsInRoot.bottom + 30f
        list.performTouchInput { moveBy(Offset(0f, belowList - title.center.y), delayMillis = 100) }
        composeRule.mainClock.advanceTimeBy(64)
        assertInsideList()
        list.performTouchInput { up() }
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithTag("managed_route_name_2").assert(hasAnyAncestor(hasTestTag("managed_route_r1")))
    }

    @Test fun headerExpandsToFullScreenAndDownwardSwipeDoesNotDismiss() {
        showRoutes()
        composeRule.onNodeWithTag("managed_route_name_0").performTextReplacement("Changed route")
        composeRule.onNodeWithTag("manage_routes_title").performTouchInput { click() }
        // Compose idleness does not wait for the system keyboard's closing animation.
        composeRule.waitUntil(5_000) {
            val bottom = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot.bottom
            val screenBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
            kotlin.math.abs(bottom - screenBottom) < 2f
        }
        val initial = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("manage_routes_title").performTouchInput {
            down(center)
            moveBy(Offset(0f, -initial.height), delayMillis = 300)
            up()
        }
        val expanded = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.height > initial.height * 1.8f)
        assertEquals(initial.bottom, expanded.bottom, 2f)
        composeRule.onNodeWithTag("manage_routes_title").performTouchInput {
            down(center)
            moveBy(Offset(0f, expanded.height), delayMillis = 300)
            up()
        }
        composeRule.onNodeWithText(label(R.string.discard_route_management_changes_title)).assertDoesNotExist()
        val collapsed = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(collapsed.height < initial.height)
        composeRule.onNodeWithTag("managed_routes_list").assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.save)).assertIsDisplayed()
        composeRule.onNodeWithText(label(R.string.cancel)).performClick()
        composeRule.onNodeWithText(label(R.string.discard_route_management_changes_title)).assertIsDisplayed()
    }

    @Test fun scrollingLongListKeepsPanelAndViewportFixed() {
        showRoutes(40)
        val panel = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNodeWithTag("managed_routes_list")
        val viewport = list.fetchSemanticsNode().boundsInRoot
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        repeat(3) {
            list.performTouchInput { swipeUp() }
            assertEquals(panel, composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot)
            assertEquals(viewport, list.fetchSemanticsNode().boundsInRoot)
        }
        list.performTouchInput { swipeDown() }
        assertEquals(panel, composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun tappingPanelHeaderClearsRouteNameFocus() {
        val start = ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START)
        val finish = ControlPoint("finish", 0, 0, Point2D(10f, 10f), ControlPointType.FINISH)
        val route = OptimizedRoute(listOf(start, finish), 100f, 0, emptyList())

        composeRule.setContent {
            val inset = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
            androidx.compose.runtime.SideEffect { keyboardInset = inset }
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

        val longName = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz12345678"
        composeRule.onNodeWithTag("managed_route_name_0")
            .performTextReplacement(longName)
        composeRule.onNodeWithTag("managed_route_name_0")
            .performTouchInput { swipeRight() }
            .assertIsFocused()
            .assertTextContains(longName)

        composeRule.onNodeWithTag("manage_routes_title")
            .performTouchInput { click() }

        composeRule.onNodeWithTag("managed_route_name_0").assertIsNotFocused()
    }
    @Test fun repeatedKeyboardCyclesKeepPanelAttachedAndRetainName() {
        showRoutes()
        repeat(4) { cycle ->
            composeRule.onNodeWithTag("managed_route_name_0").performClick()
            composeRule.waitUntil(5_000) { keyboardInset > 0 }
            composeRule.onNodeWithTag("managed_route_name_0").performTextReplacement("Rename $cycle")
            composeRule.waitUntil(5_000) {
                val panel = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
                val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
                kotlin.math.abs(panel.bottom - (root.bottom - keyboardInset)) < 2f
            }
            composeRule.onNodeWithTag("manage_routes_title").performTouchInput { click() }
            composeRule.waitUntil(5_000) { keyboardInset == 0 }
            composeRule.waitUntil(5_000) {
                val panel = composeRule.onNodeWithTag("manage_routes_panel").fetchSemanticsNode().boundsInRoot
                val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
                kotlin.math.abs(panel.bottom - root.bottom) < 2f
            }
            composeRule.onNodeWithTag("managed_route_name_0").assertTextContains("Rename $cycle")
        }
    }}
