package com.orientesanasrekinatajs.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.orientesanasrekinatajs.domain.model.*
import com.orientesanasrekinatajs.ui.components.InteractiveControlPointCanvas
import com.orientesanasrekinatajs.ui.components.RouteEditorPointList
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HapticFeedbackTest {
    @get:Rule val compose = createComposeRule()
    private val enabled = mutableStateOf(true)
    private var pulses = 0
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses++ }
    }

    @Test fun pointHoldSignalsOnceAndObservesDisabledPreference() {
        val point = ControlPoint("point", 31, 3, Point2D(50f, 50f), ControlPointType.CONTROL)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OrienteeringAppTheme(UserPreferences(vibrationFeedback = enabled.value)) {
                    InteractiveControlPointCanvas(bitmap, listOf(point), 0,
                        onPointMoved = {}, onPointSelected = {}, onViewportCenterChange = {},
                        modifier = Modifier.size(200.dp))
                }
            }
        }
        fun holdAndMove() = compose.onNodeWithTag("controlPointCanvas").performTouchInput {
            down(center)
            advanceEventTime(400)
            moveBy(Offset(1f, 0f))
            moveBy(Offset(2f, 0f))
            up()
        }
        holdAndMove()
        compose.runOnIdle { assertEquals(1, pulses); enabled.value = false }
        holdAndMove()
        compose.runOnIdle { assertEquals(1, pulses) }
    }

    @Test fun editorDragSignalsOnceAndObservesDisabledPreference() {
        val points = listOf(
            ControlPoint("start", 0, 0, Point2D(0f, 0f), ControlPointType.START),
            ControlPoint("point", 31, 3, Point2D(50f, 50f), ControlPointType.CONTROL),
            ControlPoint("finish", 0, 0, Point2D(100f, 100f), ControlPointType.FINISH),
        )
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OrienteeringAppTheme(UserPreferences(vibrationFeedback = enabled.value)) {
                    RouteEditorPointList(points, {}, {})
                }
            }
        }
        fun hold() = compose.onNodeWithTag("route_point_drag_point").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 2f))
            up()
        }
        hold()
        compose.runOnIdle { assertEquals(1, pulses); enabled.value = false }
        hold()
        compose.runOnIdle { assertEquals(1, pulses) }
    }
}
