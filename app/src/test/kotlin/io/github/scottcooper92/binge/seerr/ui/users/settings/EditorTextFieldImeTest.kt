package io.github.scottcooper92.binge.seerr.ui.users.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EditorTextFieldImeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val clear get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.field_clear)

    private fun setTwoFields(
        secondAction: ImeAction = ImeAction.Done,
        onDone: (() -> Unit)? = null,
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                var a by remember { mutableStateOf("one") }
                var b by remember { mutableStateOf("two") }
                Column {
                    EditorTextField(value = a, label = "First", onValueChange = { a = it })
                    EditorTextField(
                        value = b,
                        label = "Second",
                        imeAction = secondAction,
                        onDone = onDone,
                        onValueChange = { b = it },
                    )
                }
            }
        }
    }

    @Test
    fun `Next moves focus to the following field`() {
        setTwoFields()

        composeTestRule.onNodeWithText("First").performTextInput("x")
        composeTestRule.onNodeWithText("First").performImeAction()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Second").assertIsFocused()
    }

    @Test
    fun `Done drops focus and runs the submit callback`() {
        var submitted = false
        setTwoFields(onDone = { submitted = true })

        composeTestRule.onNodeWithText("Second").performTextInput("x")
        composeTestRule.onNodeWithText("Second").performImeAction()

        composeTestRule.onNodeWithText("Second").assertIsNotFocused()
        assertTrue(submitted)
    }

    @Test
    fun `a focused non-empty field offers a clear button that empties it`() {
        setTwoFields()
        composeTestRule.onNodeWithContentDescription(clear).assertDoesNotExist()

        composeTestRule.onNodeWithText("First").performTextInput("x")
        composeTestRule.onNodeWithContentDescription(clear).performClick()

        composeTestRule.onNodeWithText("First").assertIsFocused()
        composeTestRule.onNodeWithContentDescription(clear).assertDoesNotExist()
    }

    @Test
    fun `an empty, secret or read-only field carries no clear button`() {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                Column {
                    EditorTextField(value = "", label = "Empty", onValueChange = {})
                    EditorTextField(value = "k", label = "Secret", secret = true, onValueChange = {})
                    EditorTextField(value = "k", label = "Locked", readOnly = true, onValueChange = {})
                }
            }
        }
        listOf("Empty", "Secret", "Locked").forEach { composeTestRule.onNodeWithText(it).performClick() }

        composeTestRule.onNodeWithContentDescription(clear).assertDoesNotExist()
    }

    @Test
    fun `a multi-line field keeps Enter whatever action is asked for`() {
        assertEquals(ImeAction.Default, editorImeAction(ImeAction.Next, singleLine = false))
        assertEquals(ImeAction.Next, editorImeAction(ImeAction.Next, singleLine = true))
        assertEquals(ImeAction.Done, editorImeAction(ImeAction.Done, singleLine = true))
    }

    @Test
    fun `only the last field of a form asks for Done`() {
        assertEquals(ImeAction.Done, imeActionIf(last = true))
        assertEquals(ImeAction.Next, imeActionIf(last = false))
    }
}
