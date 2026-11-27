package com.accbot.dca.presentation.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class PasswordTextFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The field's semantic text: masked dots while hidden, the real value once shown. */
    private fun editableText(expected: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected))

    private val field get() = composeRule.onNode(hasSetTextAction())

    @Test
    fun `hidden by default and toggles visibility`() {
        composeRule.setContent {
            PasswordTextField(value = "tajne123", onValueChange = {}, label = "Password")
        }

        field.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        field.assert(!editableText("tajne123"))

        composeRule.onNodeWithContentDescription("Show password").performClick()
        field.assert(editableText("tajne123"))

        composeRule.onNodeWithContentDescription("Hide password").performClick()
        field.assert(!editableText("tajne123"))
    }
}
