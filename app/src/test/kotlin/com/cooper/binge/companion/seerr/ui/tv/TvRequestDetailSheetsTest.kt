package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.labelRes
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

private const val MEDIA_ID = 9
private const val SERVER_NAME = "Jellyseerr"

/** The TV request page's sheets for the title (#1036): marking its state, and reporting an issue on the phone. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestDetailSheetsTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val marked = mutableListOf<Triple<Int, MediaStatusChoice, Boolean>>()
    private val events = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)

    @Test
    fun markAsOpensOnTheStatesAndAPickApplies() {
        setContent(detail(media = media(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null))))
        actionButton(R.string.media_mark_as).assertIsFocused()

        pressOk()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertIsDisplayed()
        pressOk()

        assertEquals(listOf(Triple(MEDIA_ID, MediaStatusChoice.Available, false)), marked)
    }

    /** A pick the page drops, while another action is in flight or the page reloads, is not ticked as if it applied. */
    @Test
    fun aPickDroppedWhileActingIsNotTicked() {
        setContent(
            detail(media = media(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null))),
            isActing = true,
        )
        pressOk()
        pressOk()

        assertEquals(emptyList<Triple<Int, MediaStatusChoice, Boolean>>(), marked)
        composeTestRule.onNode(isSelected() and hasText(string(MediaStatusChoice.Available.labelRes()))).assertDoesNotExist()
        composeTestRule.onNode(isSelected() and hasText(string(MediaStatusChoice.Processing.labelRes()))).assertExists()
    }

    /** The sheet stays open while it saves, ticks the pick at once, says Saving, then Saved, and only then closes (#1519). */
    @Test
    fun aPickKeepsTheSheetOpenSavingThenSavedBeforeItCloses() {
        setContent(detail(media = media(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null))))
        pressOk()
        pressOk()

        composeTestRule.onNodeWithText(string(DesR.string.save_status_saving)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertIsDisplayed()
        composeTestRule.onNode(isSelected() and hasText(string(MediaStatusChoice.Available.labelRes()))).assertExists()

        composeTestRule.mainClock.autoAdvance = false
        events.tryEmit(ModerationEvent.MediaStatusSet)
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithText(string(DesR.string.save_status_saved)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(TV_SAVED_HOLD_MILLIS + 200)
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertDoesNotExist()
        actionButton(R.string.media_mark_as).assertIsFocused()
    }

    /** A refused pick puts the tick back and offers Retry in the sheet, which applies the same pick again. */
    @Test
    fun aRefusedPickMovesTheTickBackAndRetryAppliesItAgain() {
        setContent(detail(media = media(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null))))
        pressOk()
        pressOk()
        events.tryEmit(ModerationEvent.Failed(SeerrError.Unreachable))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertIsDisplayed()
        composeTestRule.onNode(isSelected() and hasText(string(MediaStatusChoice.Processing.labelRes()))).assertExists()
        composeTestRule.onNode(isSelected() and hasText(string(MediaStatusChoice.Available.labelRes()))).assertDoesNotExist()

        composeTestRule.onNode(hasText(string(DesR.string.save_status_retry)) and isFocusable()).requestFocus()
        pressOk()

        assertEquals(2, marked.size)
        assertEquals(MediaStatusChoice.Available, marked.last().second)
        composeTestRule.onNode(isSelected() and hasText(string(MediaStatusChoice.Available.labelRes()))).assertExists()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertIsDisplayed()
    }

    /** A refused pick is not the next opening's: the sheet starts clean, with no Retry for a pick that was left (#1519). */
    @Test
    fun aRefusedPickIsNotCarriedToTheNextOpening() {
        setContent(detail(media = media(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null))))
        pressOk()
        pressOk()
        events.tryEmit(ModerationEvent.Failed(SeerrError.Unreachable))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertIsDisplayed()

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        actionButton(R.string.media_mark_as).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()

        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_retry)).assertDoesNotExist()
    }

    /** A failure from the action row is not the Mark as sheet's, and a failure left in the sheet does not move under the row (#1519). */
    @Test
    fun markAsOpensWithoutAnotherActionsFailureAndLeavesNoneBehind() {
        setContent(
            detail(
                actions = RequestActions(canApprove = true),
                media = media(MediaInstance(false, SeerrMediaStatusCode.Processing, null, null, null)),
            ),
        )
        actionButton(R.string.tv_detail_approve).requestFocus()
        pressOk()
        events.tryEmit(ModerationEvent.Failed(SeerrError.Server))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertIsDisplayed()

        actionButton(R.string.media_mark_as).requestFocus()
        pressOk()
        composeTestRule.onNodeWithText(string(R.string.media_mark_as_scope_caption), substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertDoesNotExist()

        pressOk()
        events.tryEmit(ModerationEvent.Failed(SeerrError.Server))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertIsDisplayed()
        pressLeft()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertDoesNotExist()
    }

    @Test
    fun aTitleHeldTwiceAsksWhichInstanceFirst() {
        setContent(
            detail(
                media =
                    media(
                        MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null),
                        MediaInstance(true, SeerrMediaStatusCode.Processing, null, null, null),
                    ),
            ),
        )
        pressOk()
        composeTestRule.onNode(hasText(string(R.string.settings_service_4k)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        pressOk()

        assertEquals(listOf(Triple(MEDIA_ID, MediaStatusChoice.Available, true)), marked)
    }

    /** Reporting needs a keyboard, so the sheet is the title's page on the server as a code for the phone. */
    @Test
    fun aReportIsTheTitlesPageAsACodeForThePhone() {
        setContent(detail(canReportIssue = true))
        actionButton(R.string.issue_report_title).assertIsFocused()

        pressOk()

        val body = string(R.string.tv_detail_report_on_phone, SERVER_NAME)
        composeTestRule.onNodeWithText(body).assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription(body) and isFocusable()).assertIsFocused()
        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        actionButton(R.string.issue_report_title).assertIsFocused()
    }

    @Test
    fun neitherIsOfferedWithoutItsGate() {
        setContent(detail(actions = RequestActions(canRemove = true)))

        actionButton(R.string.media_mark_as).assertDoesNotExist()
        actionButton(R.string.issue_report_title).assertDoesNotExist()
    }

    /** The requester's controls are the phone's: the page offers no button for them, and the phone line names them. */
    @Test
    fun aManagerIsPointedToThePhoneForTheRequester() {
        setContent(requestDetail(mediaId = MEDIA_ID, canManageUsers = true))

        composeTestRule.onNodeWithText(string(R.string.tv_detail_phone_requester), substring = true).assertExists()
    }

    private fun media(vararg instances: MediaInstance) =
        MediaRecord(
            mediaId = MEDIA_ID,
            isTv = false,
            instances = instances.toList(),
            canSetStatus = true,
            canClearData = false,
            canDeleteFiles = false,
        )

    private fun detail(
        media: MediaRecord? = null,
        canReportIssue: Boolean = false,
        actions: RequestActions = RequestActions(),
    ) = requestDetail(actions = actions, mediaId = MEDIA_ID, canReportIssue = canReportIssue, media = media).copy(serverName = SERVER_NAME)

    private fun setContent(
        detail: RequestDetail,
        isActing: Boolean = false,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Ready(detail, isActing = isActing),
                    events = events,
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = {},
                            onOpenInBinge = null,
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlock = {},
                            onSetMediaStatus = { mediaId, status, is4k -> marked += Triple(mediaId, status, is4k) },
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** A resting icon button names itself by description and a labelled one by text; either way the surface is the focusable node. */
    private fun actionButton(label: Int) =
        composeTestRule.onNode(
            isFocusable() and
                (hasContentDescription(string(label)) or hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun pressLeft() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.waitForIdle()
    }

    private fun pressOk() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }
}
