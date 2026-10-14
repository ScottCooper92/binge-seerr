package com.cooper.binge.companion.seerr.ui.sheets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.EditRequestActions
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.RequestActionsSheet
import com.cooper.binge.companion.seerr.ui.requests.SeasonChoice
import com.cooper.binge.companion.seerr.ui.users.UserAdmissionActions
import com.cooper.binge.companion.seerr.ui.users.UserAdmissionSheets
import com.cooper.binge.companion.seerr.ui.users.UserAdmissionState
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.ui.users.UserSort
import com.cooper.binge.companion.seerr.ui.users.UsersUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

private fun string(id: Int) = RuntimeEnvironment.getApplication().getString(id)

private fun backBar() = hasContentDescription(string(DesR.string.cd_navigate_back))

/** At the expanded width the request actions sheet is a side sheet, and the editor is a page of it rather than a second sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h900dp")
class RequestActionsSheetExpandedTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var edit by mutableStateOf<EditState?>(null)
    private var starts = 0
    private var editsDismissed = 0
    private var sheetDismissed = 0

    private val editState =
        EditState(seasons = listOf(SeasonChoice(1, "Season 1", 8, selected = true, heldStatus = null)), destination = null)

    private fun show(editing: Boolean = false) {
        edit = editState.takeIf { editing }
        val detail = requestDetail(canEdit = true)
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestActionsSheet(
                    item = detail.item,
                    actions = detail.actions,
                    onApprove = {},
                    onRetry = {},
                    onDecline = {},
                    onRemove = {},
                    onDismiss = { sheetDismissed++ },
                    canEdit = true,
                    onEdit = {
                        starts++
                        edit = editState
                    },
                    edit = edit,
                    editActions =
                        EditRequestActions(
                            onToggleSeason = {},
                            onSelectAllSeasons = {},
                            onSelectServer = {},
                            onSelectProfile = {},
                            onSelectRootFolder = {},
                            onToggleTag = {},
                            onSave = {},
                            onDismiss = {
                                editsDismissed++
                                edit = null
                            },
                        ),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun openEditor() {
        rule.onNodeWithText(string(R.string.request_edit_title)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `Edit opens the editor as a page under a Back, in the same sheet`() {
        show()
        rule.onAllNodes(backBar()).assertCountEquals(0)

        openEditor()

        assertEquals(1, starts)
        rule.onAllNodes(backBar()).assertCountEquals(1)
        rule.onNodeWithText(string(R.string.request_edit_save)).assertExists()
    }

    @Test
    fun `an edit already open, begun in a narrow window, shows as a page`() {
        show(editing = true)

        assertEquals(0, starts)
        rule.onAllNodes(backBar()).assertCountEquals(1)
        rule.onNodeWithText(string(R.string.request_edit_save)).assertExists()
    }

    @Test
    fun `Back from the editor cancels the edit and returns to the actions`() {
        show()
        openEditor()

        rule.onAllNodes(backBar()).onFirst().performClick()
        rule.waitForIdle()

        assertEquals(1, editsDismissed)
        assertEquals(0, sheetDismissed)
        rule.onAllNodes(backBar()).assertCountEquals(0)
        rule.onNodeWithText(string(R.string.request_edit_title)).assertExists()
    }

    @Test
    fun `an edit that ends by itself, saved, closes its page`() {
        show()
        openEditor()

        edit = null
        rule.waitForIdle()

        rule.onAllNodes(backBar()).assertCountEquals(0)
        rule.onAllNodesWithText(string(R.string.request_edit_save)).assertCountEquals(0)
    }
}

/** The add-user flow at the expanded width: one side sheet whose form is a page over the choice, with Back to the choice. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h900dp")
class UserAdmissionSheetsExpandedTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var admission by mutableStateOf<UserAdmissionState?>(UserAdmissionState.Choosing())
    private var backToChoice = 0
    private var started = 0

    private fun show() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                UserAdmissionSheets(
                    state =
                        UsersUiState.Ready(
                            sort = UserSort.Created,
                            selection = emptySet(),
                            edit = null,
                            offered = emptyList(),
                            canAdmit = true,
                            importSource = UserOrigin.Jellyfin,
                            admission = admission,
                        ),
                    actions =
                        UserAdmissionActions(
                            onStart = {},
                            onCancel = { admission = null },
                            onBackToChoice = {
                                backToChoice++
                                admission = UserAdmissionState.Choosing()
                            },
                            onStartCreate = {
                                started++
                                admission =
                                    UserAdmissionState.Creating(
                                        com.cooper.binge.companion.seerr.ui.users
                                            .CreateUserDraft(),
                                    )
                            },
                            onEditDraft = {},
                            onCreate = {},
                            onStartImport = {},
                            onToggleCandidate = {},
                            onSelectAllCandidates = {},
                            onImport = {},
                        ),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `choosing to create opens the form as a page with a Back, and Back returns to the choice`() {
        show()
        rule.onNodeWithText(string(R.string.users_add_create)).performClick()
        rule.waitForIdle()

        assertEquals(1, started)
        rule.onAllNodes(backBar()).assertCountEquals(1)
        rule.onNodeWithText(string(R.string.users_create_title)).assertExists()

        rule.onAllNodes(backBar()).onFirst().performClick()
        rule.waitForIdle()

        assertEquals(1, backToChoice)
        rule.onAllNodes(backBar()).assertCountEquals(0)
        rule.onNodeWithText(string(R.string.users_add_create)).assertExists()
    }
}
