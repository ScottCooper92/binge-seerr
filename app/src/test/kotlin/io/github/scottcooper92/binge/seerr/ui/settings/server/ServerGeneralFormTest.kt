package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val SAVED =
    ServerGeneralSettings(applicationTitle = "Seerr", locale = "en", discoverRegion = "GB", cacheImages = true)

/** The page over a draft it edits itself, as the ViewModel would, reporting each edit (General saves as it changes) and list request. */
@Composable
private fun General(
    initial: ServerGeneralSettings,
    onEdit: (ServerGeneralSettings) -> Unit = {},
    onLoadList: (ServerList) -> Unit = {},
) = BingeExpressiveTheme(dynamicColor = false) {
    var draft by remember { mutableStateOf(initial) }
    ServerGeneralScreen(
        state =
            ExtrasEditorUiState.Ready(
                draft = draft,
                saved = SAVED,
                extras = ServerGeneralExtras(variant = SeerrVariant.Seerr),
            ),
        events = emptyFlow(),
        actions =
            EditorActions(onBack = {}, onRetry = {}, onEdit = {
                draft = it(draft)
                onEdit(draft)
            }, onSave = {}),
        keyActions = ApiKeyActions(onToggleReveal = {}, onCopy = {}, onRegenerate = {}),
        onLoadList = onLoadList,
    )
}

/** The server's General page: every group open, values edited in sheets, and the lists read only when a picker opens. */
@RunWith(RobolectricTestRunner::class)
class ServerGeneralFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val edits = mutableListOf<ServerGeneralSettings>()
    private val lists = mutableListOf<ServerList>()

    private fun show(draft: ServerGeneralSettings = SAVED) =
        rule.setContent { General(draft, onEdit = { edits += it }, onLoadList = { lists += it }) }

    @Test
    fun `every group starts open, and no list is read before its picker opens`() {
        show()

        rule.onNodeWithText("Application title").assertExists()
        rule.onNodeWithText("Cache images").performScrollTo().assertExists()
        assertEquals(emptyList<ServerList>(), lists)
    }

    @Test
    fun `an address that is not a web address keeps done off`() {
        show()

        rule.onNode(hasText("Application URL") and hasClickAction()).performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("requests.lan")

        rule.onNodeWithText("Enter a web address that starts with http:// or https://.").assertExists()
        rule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `a display language picked from the sheet is the edit that saves, with no Save to press`() {
        show()

        rule.onNode(hasText("Display language") and hasClickAction()).performClick()
        // The shared choice sheet's long list is lazy: a row is composed once scrolled to.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Deutsch"))
        rule.onNodeWithText("Deutsch").performClick()

        // The pick is the edit that saves: there is no Save to press.
        assertEquals("de", edits.last().locale)
        rule.onNodeWithText("Save").assertDoesNotExist()
    }

    @Test
    fun `opening the discover region picker asks for its list`() {
        show()

        rule.onNode(hasText("Discover region") and hasClickAction()).performScrollTo().performClick()

        assertEquals(listOf(ServerList.DiscoverRegions), lists)
    }
}

/** The server's General page on a television: OK on a row opens its sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class ServerGeneralTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `ok on a text row opens its editor`() {
        rule.setContent { General(SAVED) }
        rule.onNode(hasText("Application title") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()

        rule.onNode(hasSetTextAction() and hasText("Seerr")).assertExists()
    }
}
