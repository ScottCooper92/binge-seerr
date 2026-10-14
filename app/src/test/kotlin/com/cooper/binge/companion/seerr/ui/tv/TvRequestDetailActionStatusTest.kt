package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.ModerationEvent
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

/**
 * What each action of the request page says about itself (#1510). There is no timed plate over the page: an action's answer
 * stands under its row until the user acts again, a failure keeps a Retry within reach of the remote, and a success is the
 * page's own state refreshing, with "Saved" for as long as it takes to read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestDetailActionStatusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val events = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
    private val calls = mutableListOf<String>()

    private fun setContent(
        allowed: RequestActions,
        withMedia: Boolean = false,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Ready(requestDetail(actions = allowed, media = if (withMedia) markableMedia() else null)),
                    events = events,
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = {},
                            onOpenInBinge = null,
                            onApprove = { calls += "approve" },
                            onRetryRequest = { calls += "retry" },
                            onDecline = { calls += "decline" },
                            onRemove = { calls += "remove" },
                            onBlock = { calls += "block" },
                            onSetMediaStatus = { _, _: MediaStatusChoice, _ -> calls += "mark" },
                            onReportIssue = { _, _ -> },
                            onDismissReport = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun markableMedia() =
        MediaRecord(
            mediaId = 9,
            isTv = false,
            instances = listOf(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null)),
            canSetStatus = true,
            canClearData = false,
            canDeleteFiles = false,
        )

    private fun actionButton(label: Int) =
        composeTestRule.onNode(
            isFocusable() and
                (hasContentDescription(string(label)) or hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun press(key: Key = Key.DirectionCenter) {
        composeTestRule.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun pressRetryOnTheLine() {
        // The line comes after the action row, and an action may share the word.
        composeTestRule.onAllNodes(hasText(string(DesR.string.save_status_retry)) and isFocusable()).onLast().requestFocus()
        composeTestRule.waitForIdle()
        press()
    }

    /** Takes the action behind [label], confirming it where it asks first. */
    private fun take(
        label: Int,
        confirmLabel: Int? = null,
    ) {
        actionButton(label).requestFocus()
        composeTestRule.waitForIdle()
        press()
        if (confirmLabel != null) {
            composeTestRule.onAllNodes(hasText(string(confirmLabel)) and isFocusable()).onLast().requestFocus()
            composeTestRule.waitForIdle()
            press()
        }
    }

    private fun assertSavesThenSaves(
        label: Int,
        call: String,
        success: ModerationEvent,
        confirmLabel: Int? = null,
    ) {
        take(label, confirmLabel)
        assertEquals(listOf(call), calls)
        composeTestRule.onNodeWithText(string(DesR.string.save_status_saving)).assertExists()

        composeTestRule.mainClock.autoAdvance = false
        events.tryEmit(success)
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithText(string(DesR.string.save_status_saved)).assertExists()
        composeTestRule.mainClock.advanceTimeBy(TV_SAVED_HOLD_MILLIS + 200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_saved)).assertDoesNotExist()
    }

    private fun assertFailsThenRetries(
        label: Int,
        call: String,
        confirmLabel: Int? = null,
    ) {
        take(label, confirmLabel)
        assertEquals(listOf(call), calls)
        events.tryEmit(ModerationEvent.Failed(SeerrError.Unreachable))
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(60_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertExists()
        pressRetryOnTheLine()

        assertEquals(listOf(call, call), calls)
        composeTestRule.onNodeWithText(string(DesR.string.save_status_saving)).assertExists()
    }

    @Test
    fun approveSavesThenSaved() {
        setContent(RequestActions(canApprove = true))
        assertSavesThenSaves(R.string.tv_detail_approve, "approve", ModerationEvent.Approved)
    }

    @Test
    fun approveThatFailsKeepsARetry() {
        setContent(RequestActions(canApprove = true))
        assertFailsThenRetries(R.string.tv_detail_approve, "approve")
    }

    @Test
    fun retryRequestSavesThenSaved() {
        setContent(RequestActions(canRetry = true))
        assertSavesThenSaves(R.string.hub_retry, "retry", ModerationEvent.Retried)
    }

    @Test
    fun retryRequestThatFailsKeepsARetry() {
        setContent(RequestActions(canRetry = true))
        assertFailsThenRetries(R.string.hub_retry, "retry")
    }

    @Test
    fun declineSavesThenSaved() {
        setContent(RequestActions(canDecline = true))
        assertSavesThenSaves(R.string.tv_detail_decline, "decline", ModerationEvent.Declined)
    }

    @Test
    fun declineThatFailsKeepsARetry() {
        setContent(RequestActions(canDecline = true))
        assertFailsThenRetries(R.string.tv_detail_decline, "decline")
    }

    @Test
    fun removeThatFailsKeepsARetry() {
        setContent(RequestActions(canRemove = true))
        assertFailsThenRetries(R.string.tv_detail_remove, "remove", confirmLabel = R.string.request_remove)
    }

    @Test
    fun blockSavesThenSaved() {
        setContent(RequestActions(canBlock = true))
        assertSavesThenSaves(R.string.tv_detail_block, "block", ModerationEvent.Blocked, confirmLabel = R.string.tv_detail_block)
    }

    @Test
    fun blockThatFailsKeepsARetry() {
        setContent(RequestActions(canBlock = true))
        assertFailsThenRetries(R.string.tv_detail_block, "block", confirmLabel = R.string.tv_detail_block)
    }

    /** A decline that landed but could not block the title: the retry is the block, not a second decline. */
    @Test
    fun aDeclineThatCouldNotBlockRetriesTheBlock() {
        setContent(RequestActions(canDecline = true, canBlock = true))
        take(R.string.tv_detail_decline)
        events.tryEmit(ModerationEvent.DeclinedButBlockFailed)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertExists()
        pressRetryOnTheLine()

        assertEquals(listOf("decline", "block"), calls)
    }

    /** An approve that failed is not the Mark as sheet's: opening it shows no Retry that would approve again (#1519). */
    @Test
    fun aFailedApproveIsNotShownInTheMarkAsSheet() {
        setContent(RequestActions(canApprove = true), withMedia = true)
        take(R.string.tv_detail_approve)
        events.tryEmit(ModerationEvent.Failed(SeerrError.Unreachable))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertExists()

        take(R.string.media_mark_as)

        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertExists()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertDoesNotExist()
        assertEquals(listOf("approve"), calls)
    }

    /** A second action pressed while the first is saving is not run, and does not take over the first's Retry. */
    @Test
    fun anActionPressedWhileSavingDoesNotReplaceTheRun() {
        setContent(RequestActions(canApprove = true, canDecline = true))
        take(R.string.tv_detail_approve)
        take(R.string.tv_detail_decline)
        assertEquals(listOf("approve"), calls)

        events.tryEmit(ModerationEvent.Failed(SeerrError.Unreachable))
        composeTestRule.waitForIdle()
        pressRetryOnTheLine()

        assertEquals(listOf("approve", "approve"), calls)
    }

    @Test
    fun noOutcomeIsLaidOverThePageAsAPlate() {
        setContent(RequestActions(canApprove = true))
        take(R.string.tv_detail_approve)
        events.tryEmit(ModerationEvent.Approved)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.request_approved)).assertDoesNotExist()
    }
}
