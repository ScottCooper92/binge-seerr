package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
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
 * The request page's row leads with at most two actions, and Manage holds the rest in a sheet. Manage takes focus back from
 * whatever it opened: its own sheet, a sheet one of its rows opened, or an action it ran in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestManageFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var openedInBinge = 0

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

    @Test
    fun aRequestWithNoDecisionLeadsWithTheMediaServerAndMarkAs() {
        setContent(markable().copy(mediaServerName = "Jellyfin", canReportIssue = true), mediaServer = true)

        rowButton(string(R.string.open_in_named, "Jellyfin")).assertIsFocused()
        rowButton(R.string.media_mark_as).assertExists()
        rowButton(R.string.issue_report_title).assertDoesNotExist()

        openManage()
        sheetRow(R.string.issue_report_title).assertIsFocused()
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

    /** Block opens its confirm in Manage's place, and cancelling it lands on Manage, where the viewer came from. */
    @Test
    fun aSheetOpenedFromManageHandsFocusBackToManage() {
        setContent(requestDetail(actions = Pending))
        openManage()
        pressOk()
        composeTestRule.onNodeWithText(string(R.string.request_block_confirm_title)).assertExists()

        // The dialog is a window of its own, so it is cancelled with its button rather than the activity's Back.
        composeTestRule.onNodeWithText(string(DesR.string.action_cancel)).performClick()
        settle()

        composeTestRule.onNodeWithText(string(R.string.request_block_confirm_title)).assertDoesNotExist()
        rowButton(R.string.tv_detail_manage).assertIsFocused()
    }

    /** Report, from Manage, is the title's page as a code; Back from it lands on Manage too. */
    @Test
    fun theReportCodeOpenedFromManageHandsFocusBackToManage() {
        setContent(markable().copy(canReportIssue = true))
        openManage()
        sheetRow(R.string.issue_report_title).requestFocus()
        pressOk()
        composeTestRule.onNodeWithText(string(R.string.tv_detail_report_on_phone, "Seerr")).assertExists()

        pressBack()

        rowButton(R.string.tv_detail_manage).assertIsFocused()
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
                    events = MutableSharedFlow<ModerationEvent>(),
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
                            onBlock = {},
                            onSetMediaStatus = { _, _, _ -> },
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
