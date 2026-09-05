package com.orientesanasrekinatajs.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
