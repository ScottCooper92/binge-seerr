package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
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

/** The network page: the proxy's and the cache's settings hang beneath their switches only while they are on. */
@RunWith(RobolectricTestRunner::class)
class NetworkFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0

    private fun show(draft: NetworkForm) = rule.setContent { Network(draft) { saves++ } }

    @Test
    fun `a proxy and cache that are off show only their switches`() {
        show(ALL_OFF)

        rule.onNodeWithText("Use a proxy").performScrollTo().assertExists()
        rule.onNodeWithText("Cache DNS lookups").assertExists()
        rule.onNodeWithText("Minimum TTL").assertDoesNotExist()
        rule.onNodeWithText("Bypass for").assertDoesNotExist()
    }

    @Test
    fun `turning the proxy on shows its settings`() {
        rule.setContent { EditableNetwork(ALL_OFF) }

        rule.onNode(hasText("Use a proxy") and hasClickAction()).performScrollTo().performClick()

        rule.onNodeWithText("Bypass for").performScrollTo().assertExists()
    }

    @Test
    fun `a proxy that is on without a host can't be saved`() {
        show(ALL_OFF.copy(proxy = ProxyForm(enabled = true)))

        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `a username without a password says so`() {
        show(ALL_OFF.copy(proxy = ProxyForm(enabled = true, host = "p.lan", port = "3128", user = "seerr")))

        rule.onNodeWithText("Enter the proxy password as well").performScrollTo().assertExists()
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun `a minimum ttl above the maximum keeps done off`() {
        show(ALL_OFF.copy(dnsCache = DnsCacheForm(enabled = true, maxTtl = "5")))

        rule.onNode(hasText("Minimum TTL") and hasClickAction()).performScrollTo().performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("60")

        rule.onNodeWithText("The maximum must not be below the minimum").assertExists()
        rule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `a clean change saves`() {
        show(ALL_OFF.copy(trustProxy = true))

        rule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, saves)
    }
}

/** The network page on a television: down moves from one switch to the next. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class NetworkTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `down from trust proxy lands on csrf`() {
        rule.setContent { Network(ALL_OFF) }
        rule.onNode(hasText("Trust proxy headers") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()

        rule.onNode(hasText("CSRF protection", substring = true) and hasClickAction()).assertIsFocused()
    }
}
