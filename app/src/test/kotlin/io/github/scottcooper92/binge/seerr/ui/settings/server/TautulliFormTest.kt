package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val CONFIGURED = TautulliForm(host = "tautulli.lan", port = "8181", apiKey = "a1b2c3d4e5")

/** The Tautulli page: values checked in their sheets, the key never shown, and Save held until it can reach Tautulli. */
@RunWith(RobolectricTestRunner::class)
class TautulliFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(
        draft: TautulliForm,
        saved: TautulliForm = TautulliForm(),
    ) = rule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            TautulliScreen(
                state = EditorUiState.Ready(draft = draft, saved = saved),
                events = emptyFlow(),
                actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = { saves++ }),
            )
        }
    }

    @Test
    fun `the api key shows only its last characters`() {
        show(CONFIGURED)

        rule.onNodeWithText("•••• d4e5").assertExists()
        rule.onNodeWithText("a1b2c3d4e5").assertDoesNotExist()
    }

    @Test
    fun `a port out of range keeps done off`() {
        show(CONFIGURED)

        rule.onNode(hasText("Port") and hasClickAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("99999")

        rule.onNodeWithText("Enter a port between 1 and 65535.").assertExists()
        rule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `a form without its port or key can't be saved`() {
        show(TautulliForm(host = "tautulli.lan"))
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `a host without its port and key marks them required`() {
        show(TautulliForm(host = "tautulli.lan"))

        assertEquals(2, rule.onAllNodesWithText("Required").fetchSemanticsNodes().size)
    }

    @Test
    fun `an emptied form saves, which stops using tautulli`() {
        show(TautulliForm(), saved = CONFIGURED)

        rule.onNodeWithText("Required").assertDoesNotExist()
        rule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, saves)
    }

    @Test
    fun `a complete form saves`() {
        show(CONFIGURED)

        rule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, saves)
    }
}
