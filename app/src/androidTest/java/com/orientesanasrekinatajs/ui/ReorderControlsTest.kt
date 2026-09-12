package com.orientesanasrekinatajs.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.*
import com.orientesanasrekinatajs.ui.components.RouteEditorBottomSheet
import com.orientesanasrekinatajs.ui.components.RouteEditorPanelState
import com.orientesanasrekinatajs.ui.components.RouteEditorPointList
import com.orientesanasrekinatajs.ui.routing.RouteManagementAction
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReorderControlsTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val points = listOf(
        ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START),
        ControlPoint("a", 31, 1, Point2D(1f, 1f), ControlPointType.CONTROL),
        ControlPoint("b", 32, 1, Point2D(2f, 2f), ControlPointType.CONTROL),
        ControlPoint("c", 33, 1, Point2D(3f, 3f), ControlPointType.CONTROL),
        ControlPoint("finish", 0, 0, Point2D(4f, 4f), ControlPointType.FINISH),
    )
    private val route = OptimizedRoute(points, 100f, 3, emptyList())

    @Test fun minimizedEditorRevealsContentBeforePullIsReleased() {
        var state = RouteEditorPanelState.MINIMIZED
        compose.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    RouteEditorBottomSheet(route, points, points, state, { state = it }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("route_editor_headers").assertDoesNotExist()
        val title = compose.onNodeWithText(label(R.string.edit_route))
        title.performTouchInput {
            down(center)
            moveBy(Offset(0f, -350f), delayMillis = 300)
        }
        compose.onNodeWithTag("route_editor_headers").assertIsDisplayed()
        compose.onNodeWithTag("route_point_a").assertIsDisplayed()
        compose.runOnIdle { assertEquals(RouteEditorPanelState.MINIMIZED, state) }
        title.performTouchInput { up() }
    }

    @Test fun droppedPointSettlesFromItsReleasePosition() {
        var result = points
        compose.setContent {
            var path by remember { mutableStateOf(points) }
            OrienteeringAppTheme(UserPreferences()) {
                RouteEditorPointList(path, { result = it; path = it }, {})
            }
        }
        val handle = compose.onNodeWithTag("route_point_drag_a")
        val distance = compose.onNodeWithTag("route_point_drag_c").fetchSemanticsNode().boundsInRoot.center.y -
            handle.fetchSemanticsNode().boundsInRoot.center.y - 25f
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, distance), delayMillis = 100)
        }
        compose.mainClock.advanceTimeBy(64)
        val releaseTop = compose.onNodeWithTag("route_editor_drag_preview").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("route_editor_list").performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        val drop = compose.onNodeWithTag("route_editor_drop_preview")
        assertEquals(releaseTop, drop.fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.mainClock.advanceTimeBy(144)
        val settlingTop = drop.fetchSemanticsNode().boundsInRoot.top
        assertTrue(settlingTop > releaseTop)
        assertTrue(settlingTop < releaseTop + 26f)
        compose.mainClock.autoAdvance = true
        drop.assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("start", "b", "c", "a", "finish"), result.map { it.id }) }
    }

    @Test fun deletingPointRequiresConfirmationAndPreservesEndpoints() {
        var result = points
        compose.setContent {
            var path by remember { mutableStateOf(points) }
            OrienteeringAppTheme(UserPreferences()) {
                RouteEditorPointList(path, { result = it; path = it }, {})
            }
        }
        fun delete() = compose.onNode(hasContentDescription(label(R.string.remove_route_point)) and
            hasAnyAncestor(hasTestTag("route_point_a")))
        assertTrue(delete().fetchSemanticsNode().boundsInRoot.center.x <
            compose.onNodeWithTag("route_point_drag_a").fetchSemanticsNode().boundsInRoot.center.x)
        delete().performClick()
        compose.runOnIdle { assertEquals(points, result) }
        compose.onNodeWithText(label(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(points, result) }
        delete().performClick()
        compose.onNodeWithText(label(R.string.confirm)).performClick()
        compose.onNodeWithTag("route_point_a").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("start", "b", "c", "finish"), result.map { it.id }) }
    }

    @Test fun arrowMoveScrollsWithTheRowForRepeatedTaps() {
        val manyPoints = listOf(points.first()) + (1..30).map {
            points[1].copy(id = "p$it", code = 30 + it)
        } + points.last()
        compose.setContent {
            var path by remember { mutableStateOf(manyPoints) }
            OrienteeringAppTheme(UserPreferences()) {
                RouteEditorPointList(path, { path = it }, {})
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        val before = compose.onNodeWithTag("route_point_p10").fetchSemanticsNode().boundsInRoot.center.y
        repeat(2) {
            compose.onNode(hasContentDescription(label(R.string.move_point_down)) and
                hasAnyAncestor(hasTestTag("route_point_p10"))).performClick()
            compose.waitForIdle()
            val after = compose.onNodeWithTag("route_point_p10").fetchSemanticsNode().boundsInRoot.center.y
            assertEquals(before, after, 2f)
        }
    }

    private fun dragOtherControl(node: SemanticsNodeInteraction) {
        node.assertHasClickAction().performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 150f), delayMillis = 100)
            up()
        }
    }

    private fun drag(from: String, to: String) {
        val handle = compose.onNodeWithTag(from)
        val distance = compose.onNodeWithTag(to).fetchSemanticsNode().boundsInRoot.center.y -
            handle.fetchSemanticsNode().boundsInRoot.center.y
        handle.assertHasNoClickAction().performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, distance), delayMillis = 100)
            up()
        }
        compose.waitForIdle()
    }

    @Test fun pointArrowsAndHandleReorderWithinEndpoints() {
        var result = points
        compose.setContent {
            var path by remember { mutableStateOf(points) }
            OrienteeringAppTheme(UserPreferences()) {
                RouteEditorBottomSheet(
                    route, path, points, RouteEditorPanelState.EXPANDED, {},
                    { path = it; result = it }, {}, {},
                )
            }
        }
        val headerWidths = listOf(R.string.route_editor_number, R.string.route_editor_control, R.string.route_editor_points)
            .map { id -> compose.onNode(hasText(label(id)) and hasAnyAncestor(hasTestTag("route_editor_headers")))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.width }
        assertEquals(headerWidths[0], headerWidths[1], 1f)
        assertEquals(headerWidths[1], headerWidths[2], 1f)
        for (description in listOf(R.string.move_point_down, R.string.remove_route_point)) {
            dragOtherControl(compose.onNode(hasContentDescription(label(description)) and
                hasAnyAncestor(hasTestTag("route_point_a"))))
            compose.runOnIdle { assertEquals(points, result) }
        }
        compose.onNode(hasContentDescription(label(R.string.move_point_down)) and
            hasAnyAncestor(hasTestTag("route_point_a"))).performClick()
        compose.runOnIdle { assertEquals(listOf("start", "b", "a", "c", "finish"), result.map { it.id }) }
        compose.onNode(hasContentDescription(label(R.string.move_point_up)) and
            hasAnyAncestor(hasTestTag("route_point_a"))).performClick()
        drag("route_point_drag_a", "route_point_drag_c")
        compose.runOnIdle { assertEquals(listOf("start", "b", "c", "a", "finish"), result.map { it.id }) }
        compose.onNodeWithTag("route_point_drag_start").assertDoesNotExist()
        compose.onNodeWithTag("route_point_drag_finish").assertDoesNotExist()
        drag("route_point_drag_a", "route_point_drag_b")
        compose.runOnIdle { assertEquals(points, result) }
    }

    @Test fun pointDragPreviewsWithoutReorderingAndCancelLeavesPathUnchanged() {
        var changes = 0
        compose.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                RouteEditorPointList(points, { changes++ }, {})
            }
        }
        val handle = compose.onNodeWithTag("route_point_drag_a")
        val distance = compose.onNodeWithTag("route_point_drag_c").fetchSemanticsNode().boundsInRoot.center.y -
            handle.fetchSemanticsNode().boundsInRoot.center.y
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, distance), delayMillis = 100)
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("route_editor_drag_preview").assertExists()
        compose.onNodeWithTag("route_editor_insertion").assertExists()
        compose.runOnIdle { assertEquals(0, changes) }
        compose.onNodeWithTag("route_editor_list").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("route_editor_drag_preview").assertDoesNotExist()
        compose.onNodeWithTag("route_editor_insertion").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, changes) }
    }

    @Test fun pointDragContinuesAfterSourceScrollsAwayAndCommitsOnlyOnDrop() {
        val manyPoints = listOf(points.first()) + (1..24).map {
            points[1].copy(id = "p$it", code = 30 + it)
        } + points.last()
        var result = manyPoints
        var changes = 0
        compose.setContent {
            var path by remember { mutableStateOf(manyPoints) }
            OrienteeringAppTheme(UserPreferences()) {
                RouteEditorPointList(path, { result = it; path = it; changes++ }, {})
            }
        }
        val handle = compose.onNodeWithTag("route_point_drag_p1")
        val list = compose.onNodeWithTag("route_editor_list")
        val distance = list.fetchSemanticsNode().boundsInRoot.bottom -
            handle.fetchSemanticsNode().boundsInRoot.center.y - 8f
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, distance), delayMillis = 100)
        }
        compose.mainClock.advanceTimeBy(4000)
        compose.runOnIdle { assertEquals(0, changes) }
        compose.onNodeWithTag("route_point_drag_p1").assertDoesNotExist()
        compose.onNodeWithTag("route_editor_drag_preview").assertExists()
        list.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(1, changes)
            assertTrue(result.indexOfFirst { it.id == "p1" } > 10)
            assertEquals(manyPoints.first(), result.first())
            assertEquals(manyPoints.last(), result.last())
            assertEquals(manyPoints.map { it.id }.toSet(), result.map { it.id }.toSet())
        }
    }

    @Test fun routeArrowsAndHandlePreserveOrderOnSave() {
        val routes = listOf("a", "b", "c").map { route.copy(id = it) }
        var saved: RouteManagementAction.Apply? = null
        compose.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                ManageRoutesDialog(routes[0], routes.drop(1), 0, emptyMap(),
                    { saved = it as RouteManagementAction.Apply }, {})
            }
        }
        fun arrow(id: Int) = compose.onNode(hasContentDescription(label(id)) and
            hasAnyAncestor(hasTestTag("managed_route_a")))
        dragOtherControl(arrow(R.string.move_route_down))
        dragOtherControl(compose.onNode(hasContentDescription(label(R.string.route_actions)) and
            hasAnyAncestor(hasTestTag("managed_route_a"))))
        compose.onNodeWithTag("managed_route_name_0").assert(hasAnyAncestor(hasTestTag("managed_route_a")))
        compose.onNodeWithTag("managed_route_drag_a").performTouchInput { click() }
        compose.onNodeWithText(label(R.string.duplicate_route)).assertDoesNotExist()
        arrow(R.string.move_route_down).performClick()
        compose.onNodeWithTag("managed_route_name_1").assert(hasAnyAncestor(hasTestTag("managed_route_a")))
        arrow(R.string.move_route_up).performClick()
        compose.onNode(hasContentDescription(label(R.string.route_actions)) and
            hasAnyAncestor(hasTestTag("managed_route_a"))).assertHasClickAction()
        drag("managed_route_drag_a", "managed_route_drag_c")
        compose.onNodeWithTag("managed_route_name_2").assert(hasAnyAncestor(hasTestTag("managed_route_a")))
        drag("managed_route_drag_a", "managed_route_drag_b")
        compose.onNodeWithTag("managed_route_name_0").assert(hasAnyAncestor(hasTestTag("managed_route_a")))
        drag("managed_route_drag_a", "managed_route_drag_c")
        compose.onNodeWithText(label(R.string.save)).performClick()
        compose.runOnIdle {
            assertEquals(listOf("b", "c", "a"), saved!!.orderedRoutes.map { it.id })
            assertEquals(2, saved!!.metadata.getValue("a").order)
        }
    }

    @Test fun routeHandleScrollsAtEdgeAndStopsOnCancel() {
        val routes = (0..11).map { route.copy(id = "r$it") }
        var saved: RouteManagementAction.Apply? = null
        compose.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                ManageRoutesDialog(routes[0], routes.drop(1), 0, emptyMap(),
                    { saved = it as RouteManagementAction.Apply }, {})
            }
        }
        val handle = compose.onNodeWithTag("managed_route_drag_r0")
        val distance = compose.onNodeWithTag("managed_routes_list").fetchSemanticsNode().boundsInRoot.bottom -
            handle.fetchSemanticsNode().boundsInRoot.center.y - 10f
        compose.mainClock.autoAdvance = false
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, distance), delayMillis = 100)
        }
        compose.mainClock.advanceTimeBy(3000)
        handle.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(label(R.string.save)).performClick()
        compose.runOnIdle {
            assertEquals("r0", saved!!.orderedRoutes.last().id)
            assertEquals(routes.map { it.id }.toSet(), saved!!.orderedRoutes.map { it.id }.toSet())
        }
    }
}
