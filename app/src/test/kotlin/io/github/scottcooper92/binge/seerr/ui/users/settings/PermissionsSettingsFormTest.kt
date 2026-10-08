package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val REQUESTER = PermissionSettings(selected = setOf(ManageablePermission.Request))

/** The permissions page: every group open, Manage settings hidden, and a parent covering its children. */
@RunWith(RobolectricTestRunner::class)
class PermissionsSettingsFormTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var saves = 0
    private val toggled = mutableListOf<ManageablePermission>()

    private fun show(
        draft: PermissionSettings,
        saved: PermissionSettings = draft,
    ) = rule.setContent {
        BingeExpressiveTheme(dynamicColor = false) {
            PermissionsSettingsScreen(
                state = EditorUiState.Ready(draft = draft, saved = saved),
                events = emptyFlow(),
                actions = EditorActions(onBack = {}, onRetry = {}, onEdit = {}, onSave = { saves++ }),
                onToggle = { toggled += it },
            )
        }
    }

    @Test
    fun `every group is open and manage settings is hidden from someone without it`() {
        show(REQUESTER)

        rule.onNodeWithText("Manage users").assertExists()
        rule.onNodeWithText("Request").assertExists()
        rule.onNodeWithText("Manage settings").assertDoesNotExist()
    }

    @Test
    fun `manage settings shows for someone who already has it, so it can be taken off`() {
        show(PermissionSettings(selected = setOf(ManageablePermission.ManageSettings)))

        rule.onNodeWithText("Manage settings").assertExists()
    }

    @Test
    fun `a parent covers its children, which read on and can't be flipped`() {
        show(PermissionSettings(selected = setOf(ManageablePermission.ManageRequests)))

        rule
            .onNode(hasText("View requests") and hasClickAction())
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertEquals(emptyList<ManageablePermission>(), toggled)
    }

    @Test
    fun `a row flips its permission and a changed draft saves`() {
        show(REQUESTER.copy(selected = setOf(ManageablePermission.Request4k)), saved = REQUESTER)

        rule
            .onNode(hasText("Request") and hasClickAction())
            .performScrollTo()
            .assertIsOff()
            .performClick()
        rule.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(ManageablePermission.Request), toggled)
        assertEquals(1, saves)
    }
}

/** The permissions page on a television: down moves from one permission to the next. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class PermissionsSettingsTvFocusTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `down from admin lands on manage users`() {
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
        rule.onNode(hasText("Admin") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        rule.waitForIdle()

        rule.onNode(hasText("Manage users") and hasClickAction()).assertIsFocused()
    }
}
