package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.PermissionGroup
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val REQUESTER = PermissionSettings(selected = setOf(ManageablePermission.Request))

class PermissionGroupStartsOpenTest {
    @Test
    fun `administration starts closed when it grants nothing`() {
        assertFalse(permissionGroupStartsOpen(PermissionGroup.Administration, setOf(ManageablePermission.Request)))
    }

    @Test
    fun `administration starts open when it already grants something`() {
        assertTrue(permissionGroupStartsOpen(PermissionGroup.Administration, setOf(ManageablePermission.ManageUsers)))
    }

    @Test
    fun `every other group starts open`() {
        PermissionGroup.entries.filter { it != PermissionGroup.Administration }.forEach { group ->
            assertTrue("$group", permissionGroupStartsOpen(group, emptySet()))
        }
    }
}

/** The permissions page's sections and its pinned bar. */
@RunWith(RobolectricTestRunner::class)
class PermissionsSettingsFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0
    private var backs = 0

    private fun show(
        draft: PermissionSettings,
        saved: PermissionSettings = draft,
    ) = rule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            PermissionsSettingsScreen(
                state = EditorUiState.Ready(draft = draft, saved = saved),
                events = emptyFlow(),
                actions = EditorActions(onBack = { backs++ }, onRetry = {}, onEdit = {}, onSave = { saves++ }),
                onToggle = {},
            )
        }
    }

    @Test
    fun `administration is closed and the request toggles are open for a plain requester`() {
        show(REQUESTER)

        rule.onNodeWithText("Manage settings").assertDoesNotExist()
        rule.onNodeWithText("Request").assertExists()
    }

    @Test
    fun `administration opens when its header is activated`() {
        show(REQUESTER)

        rule.onNodeWithText("Administration").performClick()

        rule.onNodeWithText("Manage settings").assertExists()
    }

    @Test
    fun `a changed draft saves from the pinned bar`() {
        show(REQUESTER.copy(selected = setOf(ManageablePermission.Request4k)), saved = REQUESTER)

        rule.onNodeWithText("Save").assertIsDisplayed().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `cancel leaves`() {
        show(REQUESTER)

        rule.onNodeWithText("Cancel").performClick()

        assertEquals(1, backs)
    }
}

/** The permissions page on a television: a closed header is one stop, and down moves on to the next one. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class PermissionsSettingsTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `down from the closed administration header lands on the requests header`() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                PermissionsSettingsScreen(
                    state = EditorUiState.Ready(draft = REQUESTER, saved = REQUESTER),
                    events = emptyFlow(),
                    actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = {}),
                    onToggle = {},
                )
            }
        }
        rule.onNodeWithText("Administration").performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()

        rule.onNodeWithText("Administration").assertIsNotFocused()
        rule.onNodeWithText("Requests").assertIsFocused()
    }
}
