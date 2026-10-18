package com.cooper.binge.companion.seerr.ui.sheets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.requests.EditRequestActions
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.ManageMediaActions
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.RequestActionsSheet
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestManagementSheets
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

/** At the expanded width Mark as is a page of the actions sheet too, and below it stays a sheet of its own (#1578). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h900dp")
class MarkAsPageExpandedTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val instance =
        MediaInstance(is4k = false, status = SeerrMediaStatusCode.Processing, serviceUrl = null, mediaServerUrl = null, watch = null)
    private var marking by mutableStateOf<MediaInstance?>(null)
    private val picked = mutableListOf<MediaStatusChoice>()
    private var markDismissed = 0
    private var sheetDismissed = 0

    private fun show() {
        val detail = requestDetail()
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
                    media =
                        MediaRecord(
                            mediaId = 900,
                            isTv = false,
                            instances = listOf(instance),
                            canSetStatus = true,
                            canClearData = false,
                            canDeleteFiles = false,
                        ),
                    mediaActions = ManageMediaActions(onSetStatus = { _, _, _ -> }, onClearData = {}, onDeleteFiles = { _, _ -> }),
                    onMarkStatus = { marking = instance },
                    marking = marking,
                    onMarkSelect = { picked += it },
                    onMarkDismiss = {
                        markDismissed++
                        marking = null
                    },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun openMarkAs() {
        rule.onNodeWithText(string(R.string.media_instance_standard)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun `Mark as opens as a page under a Back, in the same sheet`() {
        show()
        rule.onAllNodes(backBar()).assertCountEquals(0)

        openMarkAs()

        rule.onAllNodes(backBar()).assertCountEquals(1)
        rule.onNodeWithText(string(R.string.media_mark_as)).assertExists()
        rule.onNodeWithText(string(R.string.media_state_available)).assertExists()
    }

    @Test
    fun `a pick is applied and the page steps back to the actions`() {
        show()
        openMarkAs()

        rule.onNodeWithText(string(R.string.media_state_available)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(MediaStatusChoice.Available), picked)
        assertEquals(1, markDismissed)
        assertEquals(0, sheetDismissed)
        rule.onAllNodes(backBar()).assertCountEquals(0)
        rule.onNodeWithText(string(R.string.media_instance_standard)).assertExists()
    }

    @Test
    fun `Back from the page picks nothing and returns to the actions`() {
        show()
        openMarkAs()

        rule.onAllNodes(backBar()).onFirst().performClick()
        rule.waitForIdle()

        assertEquals(emptyList<MediaStatusChoice>(), picked)
        assertEquals(1, markDismissed)
        assertEquals(0, sheetDismissed)
        rule.onAllNodes(backBar()).assertCountEquals(0)
    }
}

/** Closing the side sheet from the Mark as page drops the mark, so Mark as does not reopen as a bottom sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h900dp")
class MarkAsPageDismissTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var acting by mutableStateOf(true)

    @Test
    fun `a scrim tap on the Mark as page closes the sheet and leaves no Mark as behind`() {
        val instance =
            MediaInstance(is4k = false, status = SeerrMediaStatusCode.Processing, serviceUrl = null, mediaServerUrl = null, watch = null)
        val detail =
            requestDetail(
                media =
                    MediaRecord(
                        mediaId = 900,
                        isTv = false,
                        instances = listOf(instance),
                        canSetStatus = true,
                        canClearData = false,
                        canDeleteFiles = false,
                    ),
            )
        val editActions =
            EditRequestActions(
                onToggleSeason = {},
                onSelectAllSeasons = {},
                onSelectServer = {},
                onSelectProfile = {},
                onSelectRootFolder = {},
                onToggleTag = {},
                onSave = {},
                onDismiss = {},
            )
        val actions =
            RequestDetailActions(
                onBack = {},
                onRetry = {},
                onReportIssue = { _, _ -> },
                onDismissReport = {},
                onApprove = {},
                onRetryRequest = {},
                onDecline = {},
                onRemove = {},
                onStartEdit = {},
                siblingSheet = {},
                onOpenUser = {},
                edit = editActions,
                media = ManageMediaActions(onSetStatus = { _, _, _ -> }, onClearData = {}, onDeleteFiles = { _, _ -> }),
            )
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestManagementSheets(
                    state = RequestDetailUiState.Ready(detail),
                    actions = actions,
                    acting = acting,
                    onDismissActing = { acting = false },
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.media_instance_standard)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.media_mark_as)).assertExists()

        rule.onAllNodes(isRoot()).onLast().performTouchInput { click(Offset(8f, 8f)) }
        rule.waitForIdle()

        assertEquals(false, acting)
        rule.onAllNodesWithText(string(R.string.media_mark_as)).assertCountEquals(0)
        rule.onAllNodesWithText(string(R.string.media_state_available)).assertCountEquals(0)
    }
}

/** Closing the side sheet from the editor page ends the edit, so the editor does not reopen as a bottom sheet (#1654). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h900dp")
class EditorPageDismissTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var acting by mutableStateOf(true)
    private var state by mutableStateOf(RequestDetailUiState.Ready(requestDetail(canEdit = true)))
    private var editsDismissed = 0

    private val editState =
        EditState(seasons = listOf(SeasonChoice(1, "Season 1", 8, selected = true, heldStatus = null)), destination = null)

    @Test
    fun `a scrim tap on the editor page closes the sheet and ends the edit`() {
        val actions =
            RequestDetailActions(
                onBack = {},
                onRetry = {},
                onReportIssue = { _, _ -> },
                onDismissReport = {},
                onApprove = {},
                onRetryRequest = {},
                onDecline = {},
                onRemove = {},
                onStartEdit = { state = state.copy(edit = editState) },
                siblingSheet = {},
                onOpenUser = {},
                edit =
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
                            state = state.copy(edit = null)
                        },
                    ),
                media = ManageMediaActions(onSetStatus = { _, _, _ -> }, onClearData = {}, onDeleteFiles = { _, _ -> }),
            )
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestManagementSheets(state = state, actions = actions, acting = acting, onDismissActing = { acting = false })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.request_edit_title)).performClick()
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.request_edit_save)).assertExists()

        rule.onAllNodes(isRoot()).onLast().performTouchInput { click(Offset(8f, 8f)) }
        rule.waitForIdle()

        assertEquals(false, acting)
        assertEquals(1, editsDismissed)
        rule.onAllNodesWithText(string(R.string.request_edit_save)).assertCountEquals(0)
    }
}

/** Below the expanded width the actions sheet stacks no pages, so Mark as is not one of them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class MarkAsPageNarrowTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a bottom sheet shows no page for Mark as`() {
        val detail = requestDetail()
        val instance =
            MediaInstance(is4k = false, status = SeerrMediaStatusCode.Processing, serviceUrl = null, mediaServerUrl = null, watch = null)
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestActionsSheet(
                    item = detail.item,
                    actions = detail.actions,
                    onApprove = {},
                    onRetry = {},
                    onDecline = {},
                    onRemove = {},
                    onDismiss = {},
                    marking = instance,
                )
            }
        }
        rule.waitForIdle()

        rule.onAllNodes(backBar()).assertCountEquals(0)
        rule.onAllNodesWithText(string(R.string.media_state_available)).assertCountEquals(0)
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
