package io.github.scottcooper92.binge.seerr.ui.users.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SECRET = "hunter2"
private const val LABEL = "API key"

/** The default mask a password transformation applies, which is what a masked field puts on screen. */
private val MASKED = "•".repeat(SECRET.length)

/**
 * The reveal toggle every masked field now carries. The assertions are on the pair a user meets:
 * what the field puts on screen, and what the button says the next tap will do.
 *
 * `EditableText` is the transformed text and `InputText` the raw value, so `hasText` finds the
 * secret either way and only the former answers whether the field is masked.
 */
@RunWith(RobolectricTestRunner::class)
class EditorTextFieldRevealTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val show get() = context.getString(R.string.field_secret_show)
    private val hide get() = context.getString(R.string.field_secret_hide)

    private fun setField(
        secret: Boolean = true,
        contentType: ContentType? = null,
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                var value by remember { mutableStateOf(SECRET) }
                EditorTextField(
                    value = value,
                    label = LABEL,
                    secret = secret,
                    contentType = contentType,
                    onValueChange = { value = it },
                )
            }
        }
    }

    private fun onScreen(): String =
        composeTestRule
            .onNodeWithText(LABEL)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    @Test
    fun `a masked field starts hidden and the toggle offers to show it`() {
        setField()

        composeTestRule.onNodeWithContentDescription(show).assertIsDisplayed()
        assertEquals(MASKED, onScreen())
    }

    @Test
    fun `tapping the toggle reveals the value and offers to hide it again`() {
        setField()

        composeTestRule.onNodeWithContentDescription(show).performClick()

        composeTestRule.onNodeWithContentDescription(hide).assertIsDisplayed()
        assertEquals(SECRET, onScreen())

        composeTestRule.onNodeWithContentDescription(hide).performClick()

        composeTestRule.onNodeWithContentDescription(show).assertIsDisplayed()
        assertEquals(MASKED, onScreen())
    }

    @Test
    fun `a field that is not secret carries no toggle`() {
        setField(secret = false)

        composeTestRule.onNodeWithContentDescription(show).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(hide).assertDoesNotExist()
        assertEquals(SECRET, onScreen())
    }

    @Test
    fun `revealing leaves the credential provider's content type alone`() {
        setField(contentType = ContentType.Password)

        composeTestRule.onNodeWithContentDescription(show).performClick()

        val isPasswordField = SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.Password)
        assertTrue(composeTestRule.onAllNodes(isPasswordField).fetchSemanticsNodes().isNotEmpty())
    }
}
