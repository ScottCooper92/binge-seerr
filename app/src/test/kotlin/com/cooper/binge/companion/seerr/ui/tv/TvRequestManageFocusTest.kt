package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.ModerationEvent
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

private val Pending = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true)

/**
 * The request page's row leads with at most two actions, and Manage holds the rest in a stack of pages. A page a row opens
 * hands focus back to that row, and so does a confirm cancelled over Manage; Manage's own button takes it back when Manage
 * closes, after an action it ran or a confirm answered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestManageFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var openedInBinge = 0
    private var blocks = 0
    private val marked = mutableListOf<MediaStatusChoice>()
    private val events = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)

    @Test
    fun aPendingRequestLeadsWithItsDecisionAndManageHoldsTheRest() {
        setContent(requestDetail(actions = Pending))

        rowButton(R.string.tv_detail_approve).assertIsFocused()
        rowButton(R.string.tv_detail_decline).assertExists()
        rowButton(R.string.tv_detail_remove).assertDoesNotExist()
        rowButton(R.string.tv_detail_block).assertDoesNotExist()

        openManage()

        sheetRow(R.string.tv_detail_block).assertIsFocused()
        val block = sheetRow(R.string.tv_detail_block).getBoundsInRoot()
        val remove = sheetRow(R.string.tv_detail_remove).getBoundsInRoot()
        assertTrue("Remove, the irreversible one, is last", remove.top > block.top)
    }

    /** With no decision to make, three of the title's own actions lead: the media server, Binge and Mark as. Report is managed. */
    @Test
    fun aRequestWithNoDecisionLeadsWithItsTitleActions() {
        setContent(markable().copy(mediaServerName = "Jellyfin", canReportIssue = true), mediaServer = true, openInBinge = true)

        rowButton(string(R.string.open_in_named, "Jellyfin")).assertIsFocused()
        rowButton(R.string.request_open_binge).assertExists()
        rowButton(R.string.media_mark_as).assertExists()
        rowButton(R.string.issue_report_title).assertDoesNotExist()

        openManage()
        sheetRow(R.string.issue_report_title).assertIsFocused()
    }

    /** Where Binge would answer and there is no media server app, Binge, Mark as and Report all lead. */
    @Test
    fun withoutAMediaServerAppReportLeadsBesideBinge() {
        setContent(markable().copy(canReportIssue = true), openInBinge = true)

        rowButton(R.string.request_open_binge).assertIsFocused()
        rowButton(R.string.media_mark_as).assertExists()
        rowButton(R.string.issue_report_title).assertExists()
    }

    @Test
    fun reportLeadsWhereTheTitleCannotBeMarked() {
        setContent(requestDetail(canReportIssue = true, actions = RequestActions(canRemove = true)))

        rowButton(R.string.issue_report_title).assertIsFocused()
        rowButton(R.string.tv_detail_manage).assertExists()
    }

    @Test
    fun withNothingPastTheLeadsThereIsNoManage() {
        setContent(requestDetail(actions = RequestActions(canApprove = true, canDecline = true)))

        rowButton(R.string.tv_detail_approve).assertIsFocused()
        rowButton(R.string.tv_detail_manage).assertDoesNotExist()
    }

    @Test
    fun backFromManageLandsOnManage() {
        setContent(requestDetail(actions = Pending))
        openManage()

        pressBack()

        sheetRow(R.string.tv_detail_block).assertDoesNotExist()
        rowButton(R.string.tv_detail_manage).assertIsFocused()
    }

    /** Block asks over Manage, and cancelling it lands on Block's row, where the viewer was. */
    @Test
    fun cancellingBlockFromManageLandsOnItsRow() {
        setContent(requestDetail(actions = Pending))
        openManage()
        pressOk()
        composeTestRule.onNodeWithText(string(R.string.request_block_confirm_title)).assertExists()

        // The dialog is a window of its own, so it is cancelled with its button rather than the activity's Back.
        composeTestRule.onNodeWithText(string(DesR.string.action_cancel)).performClick()
        settle()

        composeTestRule.onNodeWithText(string(R.string.request_block_confirm_title)).assertDoesNotExist()
        assertEquals(0, blocks)
        sheetRow(R.string.tv_detail_block).assertIsFocused()
    }

    /** An answered Block runs, and closes Manage with it: focus is back on Manage's button. */
    @Test
    fun confirmingBlockFromManageBlocksAndClosesManage() {
        setContent(requestDetail(actions = Pending))
        openManage()
        pressOk()

        composeTestRule
            .onAllNodes(hasText(string(R.string.tv_detail_block)) and isFocusable())
            .filterToOne(hasAnyAncestor(isDialog()))
            .performClick()
        settle()

        assertEquals(1, blocks)
        sheetRow(R.string.tv_detail_remove).assertDoesNotExist()
        rowButton(R.string.tv_detail_manage).assertIsFocused()
    }

    /** Report, from Manage, is a page in Manage's stack: Back returns to its row, and Back again closes Manage. */
    @Test
    fun theReportCodeOpenedFromManageGoesBackToItsRow() {
        setContent(requestDetail(actions = Pending, canReportIssue = true))
        openManage()
        sheetRow(R.string.issue_report_title).requestFocus()
        pressOk()
        composeTestRule
            .onNodeWithText(
                string(R.string.tv_detail_report_on_phone, "Seerr") + "\n" + string(R.string.tv_detail_report_scan_hint),
            ).assertExists()

        pressBack()
        sheetRow(R.string.issue_report_title).assertIsFocused()

        pressBack()
        rowButton(R.string.tv_detail_manage).assertIsFocused()
    }

    /** Mark as, from Manage, applies in place and stays open once saved; Back returns to its row in Manage. */
    @Test
    fun markAsOpenedFromManageStaysOpenAndGoesBackToItsRow() {
        setContent(markable().copy(actions = Pending))
        openManage()
        sheetRow(R.string.media_mark_as).requestFocus()
        pressOk()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertExists()
        pressOk()
        assertEquals(listOf(MediaStatusChoice.Available), marked)

        events.tryEmit(ModerationEvent.MediaStatusSet)
        composeTestRule.mainClock.advanceTimeBy(TV_SAVED_HOLD_MILLIS + 200)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertExists()

        pressBack()
        sheetRow(R.string.media_mark_as).assertIsFocused()
    }

    @Test
    fun anActionWithoutASheetRunsFromManageAndFocusReturnsToManage() {
        setContent(requestDetail(actions = Pending), openInBinge = true)
        openManage()
        sheetRow(R.string.request_open_binge).requestFocus()
        pressOk()
        settle()

        assertEquals(1, openedInBinge)
        sheetRow(R.string.tv_detail_block).assertDoesNotExist()
        rowButton(R.string.tv_detail_manage).assertIsFocused()
    }

    private fun markable(): RequestDetail =
        requestDetail(
            actions = RequestActions(canRemove = true),
            media =
                MediaRecord(
                    mediaId = 9,
                    isTv = false,
                    instances = listOf(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null)),
                    canSetStatus = true,
                    canClearData = false,
                    canDeleteFiles = false,
                ),
        )

    private fun setContent(
        detail: RequestDetail,
        mediaServer: Boolean = false,
        openInBinge: Boolean = false,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Ready(detail),
                    events = events,
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = {},
                            onOpenInBinge = if (openInBinge) ({ openedInBinge++ }) else null,
                            onOpenInMediaServer = if (mediaServer) ({}) else null,
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlock = { blocks++ },
                            onSetMediaStatus = { _, status, _ -> marked += status },
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun openManage() {
        rowButton(R.string.tv_detail_manage).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
    }

    /** A resting icon button names itself by description and a labelled one by text; either way the surface is the focusable node. */
    private fun rowButton(label: Int) = rowButton(string(label))

    private fun rowButton(label: String) =
        composeTestRule.onNode(
            isFocusable() and (hasContentDescription(label) or hasText(label) or hasAnyDescendant(hasText(label))),
        )

    private fun sheetRow(label: Int) =
        composeTestRule.onNode(
            isFocusable() and (hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun pressOk() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    /** A closer hands focus back a frame after the sheet goes. */
    private fun settle() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }
}
