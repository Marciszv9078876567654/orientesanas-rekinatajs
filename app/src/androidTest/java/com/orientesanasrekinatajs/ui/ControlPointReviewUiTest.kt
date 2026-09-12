package com.orientesanasrekinatajs.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.test.platform.app.InstrumentationRegistry
import com.orientesanasrekinatajs.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.UserPreferences
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import org.junit.Rule
import org.junit.Test

class ControlPointReviewUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun duplicateManualCodeWarnsButCanBeSavedForReview() {
        val point = ControlPoint(id = "edited", code = 31, center = Point2D(0f, 0f))
        val other = point.copy(id = "other", code = 65)
        var saved: ControlPoint? = null
        composeRule.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                ControlPointEditorDialog(point, listOf(point, other), false,
                    { saved = it }, {}, {})
            }
        }
        composeRule.onNodeWithTag("controlPointCode").performTextReplacement("65")
        composeRule.onNodeWithTag("duplicateControlCodeWarning").assertExists()
        composeRule.onNodeWithText(InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.save)).assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(65, saved?.code)
            assertTrue(saved!!.needsReview)
        }
    }

    @Test
    fun uniqueCodeAndOwnCodeDoNotWarnAndClearReviewFlag() {
        val point = ControlPoint(id = "edited", code = 31, center = Point2D(0f, 0f), needsReview = true)
        val other = point.copy(id = "other", code = 65)
        var saved: ControlPoint? = null
        composeRule.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                ControlPointEditorDialog(point, listOf(point, other), false,
                    { saved = it }, {}, {})
            }
        }
        composeRule.onNodeWithTag("controlPointCode").performTextReplacement("65")
        composeRule.onNodeWithTag("duplicateControlCodeWarning").assertExists()
        composeRule.onNodeWithTag("controlPointCode").performTextReplacement("31")
        composeRule.onNodeWithTag("duplicateControlCodeWarning").assertDoesNotExist()
        composeRule.onNodeWithText(InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.save)).assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(31, saved?.code)
            assertEquals(false, saved?.needsReview)
        }
    }

    @Test
    fun flaggedPointEditorStartsWithEmptyCode() {
        composeRule.setContent {
            OrienteeringAppTheme(UserPreferences()) {
                ControlPointEditorDialog(
                    point = ControlPoint(
                        code = 0,
                        center = Point2D(20f, 20f),
                        needsReview = true,
                    ),
                    isNew = false,
                    points = emptyList(),
                    onSave = {},
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("controlPointCode").assert(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString(""),
            ),
        )
    }
}
