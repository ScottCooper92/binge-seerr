package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performImeAction
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A single-line text setting's sheet submits from the keyboard's Done, and only while its problem is clear. */
@RunWith(RobolectricTestRunner::class)
class TextSettingContentImeTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private var submitted = 0

    private fun show(
        multiline: Boolean = false,
        problem: String? = null,
    ) = composeTestRule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            var draft by remember { mutableStateOf("value") }
            TextSettingContent(
                title = "Setting",
                draft = draft,
                onDraftChange = { draft = it },
                onSubmit = { submitted++ },
                onCancel = {},
                problem = problem,
                multiline = multiline,
            )
        }
    }

    @Test
    fun `Done on a single line field submits`() {
        show()

        composeTestRule.onNode(hasSetTextAction()).performImeAction()
        composeTestRule.waitForIdle()

        assertEquals(1, submitted)
    }

    @Test
    fun `Done does nothing while the setting has a problem`() {
        show(problem = "Not a number")

        composeTestRule.onNode(hasSetTextAction()).performImeAction()
        composeTestRule.waitForIdle()

        assertEquals(0, submitted)
    }
}
