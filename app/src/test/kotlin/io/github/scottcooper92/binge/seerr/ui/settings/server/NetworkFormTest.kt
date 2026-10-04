package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
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
import org.robolectric.annotation.Config

private val ALL_OFF = NetworkForm(forceIpv4First = false, proxy = ProxyForm(), dnsCache = DnsCacheForm())

@Composable
private fun Network(
    draft: NetworkForm,
    onSave: () -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    NetworkScreen(
        state = EditorUiState.Ready(draft = draft, saved = ALL_OFF),
        events = emptyFlow(),
        actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = onSave),
    )
}

@Composable
private fun EditableNetwork(initial: NetworkForm) {
    var draft by remember { mutableStateOf(initial) }
    BingeExpressiveTheme(dynamicColor = false) {
        NetworkScreen(
            state = EditorUiState.Ready(draft = draft, saved = ALL_OFF),
            events = emptyFlow(),
            actions = EditorActions(onBack = {}, onRetry = {}, onEdit = { draft = it(draft) }, onSave = {}),
        )
    }
}

/** The network page's sections: a proxy or cache that is off starts closed, and one that is on and wrong blocks Save. */
@RunWith(RobolectricTestRunner::class)
class NetworkFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: NetworkForm) = rule.setContent { Network(draft) { saves++ } }

    @Test
    fun `a proxy and cache that are off start closed`() {
        show(ALL_OFF)

        rule.onNodeWithText("Trust proxy headers").assertExists()
        rule.onNodeWithText("Use a proxy").assertDoesNotExist()
        rule.onNodeWithText("Cache DNS lookups").assertDoesNotExist()
    }

    @Test
    fun `a proxy that is on without a host shows required only after save is tried`() {
        show(ALL_OFF.copy(proxy = ProxyForm(enabled = true)))

        assertEquals(0, rule.onAllNodesWithText("Required").fetchSemanticsNodes().size)
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()

        assertEquals(0, saves)
        assertEquals(2, rule.onAllNodesWithText("Required").fetchSemanticsNodes().size)
    }

    @Test
    fun `turning off a proxy that started on keeps its section open`() {
        rule.setContent {
            EditableNetwork(ALL_OFF.copy(proxy = ProxyForm(enabled = true, host = "proxy.local", port = "8080")))
        }

        rule.onNodeWithText("Use a proxy").assertExists()
        rule.onNodeWithText("Use a proxy").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Use a proxy").assertExists()
    }

    @Test
    fun `a clean change saves`() {
        show(ALL_OFF.copy(trustProxy = true))

        rule.onNodeWithText("Save").performClick()

        assertEquals(1, saves)
    }
}

/** The network page on a television: a closed section is one stop, and down moves on to the next header. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class NetworkTvFocusTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `down from the closed proxy header lands on the dns cache header`() {
        rule.setContent { Network(ALL_OFF) }
        rule.onNodeWithText("Outbound proxy").performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()

        rule.onNodeWithText("Outbound proxy").assertIsNotFocused()
        rule.onNodeWithText("DNS cache").assertIsFocused()
    }
}
