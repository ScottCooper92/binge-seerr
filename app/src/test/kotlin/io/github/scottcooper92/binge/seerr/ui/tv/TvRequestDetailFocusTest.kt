package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationScope
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsRowsBoard
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardAndroidComposeRule
import io.github.scottcooper92.binge.seerr.util.requestDetail
import io.github.scottcooper92.binge.seerr.util.requestItem
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val HEAT = "Heat"

/**
 * The board's row through to its read-only detail page and back, under a real Back key rather than a
 * directional dismiss: OK on the row reaches the page with the D-pad, and Back returns focus to the row
 * that opened it. The page's own primary action is covered here too, since it is what the row's moderation
 * moved onto: Approve and Decline run at once from the action row, and Remove and Block ask first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestDetailFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val approved = mutableListOf<Int>()
    private val declined = mutableListOf<Boolean>()
    private val removed = mutableListOf<Boolean>()
    private var blocked = 0

    /** What the page offers, which a successful moderation changes as the real page's reload does. */
    private var offered by mutableStateOf(RequestActions())

    @Test
    fun okOnTheRowReachesThePageOnItsFirstActionAndBackReturnsFocusToTheRow() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        pressOk()
        actionButton(R.string.tv_detail_approve).assertIsFocused()

        pressBack()
        settleFocusRestore()

        row(HEAT).assertIsFocused()
    }

    @Test
    fun approveRunsAtOnceAndKeepsFocusOnTheButton() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_approve).assertIsFocused()

        pressOk()

        assertEquals(listOf(1), approved)
        actionButton(R.string.tv_detail_approve).assertIsFocused()
    }

    @Test
    fun declineIsAStepRightOfApproveAndKeepsTheRequest() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()

        pressRight()
        actionButton(R.string.tv_detail_decline).assertIsFocused()
        pressOk()

        assertEquals(listOf(false), declined)
    }

    @Test
    fun removeAsksFirstAndCancelReturnsToTheButton() {
        setContent(RequestActions(canRemove = true))
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_remove).assertIsFocused()

        pressOk()
        composeTestRule.onNodeWithText(string(R.string.request_remove_confirm_title)).assertIsDisplayed()
        assertEquals(emptyList<Boolean>(), removed)
        pressBack()
        settleFocusRestore()

        assertEquals(emptyList<Boolean>(), removed)
        actionButton(R.string.tv_detail_remove).assertIsFocused()
    }

    @Test
    fun blockingTheTitleAsksFirstThenBlocksOnConfirm() {
        setContent(RequestActions(canBlock = true))
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_block).assertIsFocused()

        pressOk()
        composeTestRule.onNodeWithText(string(R.string.request_block_confirm_title)).assertIsDisplayed()
        assertEquals(0, blocked)
        // The sheet's confirm row shares its label with the button beneath it; it is the later of the two.
        composeTestRule.onAllNodes(hasText(string(R.string.tv_detail_block)) and isFocusable()).onLast().requestFocus()
        composeTestRule.waitForIdle()
        pressOk()

        assertEquals(1, blocked)
    }

    /** A successful approve reloads the page without Approve; focus stays on the action row, not the root (#801). */
    @Test
    fun approvingRemovesApproveAndFocusStaysOnTheActionRow() {
        setContent(afterApprove = RequestActions(canDecline = true))
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_approve).assertIsFocused()

        pressOk()
        composeTestRule.waitForIdle()

        actionButton(R.string.tv_detail_approve).assertDoesNotExist()
        actionButton(R.string.tv_detail_decline).assertIsFocused()
    }

    /** A successful decline reloads the page without Approve or Decline; focus moves to what is left on the row (#801). */
    @Test
    fun decliningRemovesDeclineAndFocusStaysOnTheActionRow() {
        setContent(
            allowed = RequestActions(canApprove = true, canDecline = true, canRemove = true),
            afterDecline = RequestActions(canRemove = true),
        )
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        pressRight()
        actionButton(R.string.tv_detail_decline).assertIsFocused()

        pressOk()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false), declined)
        actionButton(R.string.tv_detail_decline).assertDoesNotExist()
        actionButton(R.string.tv_detail_remove).assertIsFocused()
    }

    /**
     * A successful block reloads the page without Block, after the confirm sheet has handed focus back to it, as the
     * real reload lands once the server answers; focus moves to what is left on the row (#801).
     */
    @Test
    fun blockingRemovesBlockAndFocusStaysOnTheActionRow() {
        setContent(RequestActions(canBlock = true, canRemove = true))
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_remove).assertIsFocused()
        pressRight()
        actionButton(R.string.tv_detail_block).assertIsFocused()
        pressOk()
        composeTestRule.onAllNodes(hasText(string(R.string.tv_detail_block)) and isFocusable()).onLast().requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        settleFocusRestore()
        assertEquals(1, blocked)
        actionButton(R.string.tv_detail_block).assertIsFocused()

        offered = RequestActions(canRemove = true)
        composeTestRule.waitForIdle()

        actionButton(R.string.tv_detail_block).assertDoesNotExist()
        actionButton(R.string.tv_detail_remove).assertIsFocused()
    }

    /** A block that leaves no action behind hands focus to the synopsis rather than the root (#801). */
    @Test
    fun blockingTheLastActionLeavesFocusOnTheSynopsis() {
        setContent(RequestActions(canBlock = true))
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_block).assertIsFocused()
        pressOk()
        composeTestRule.onAllNodes(hasText(string(R.string.tv_detail_block)) and isFocusable()).onLast().requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        settleFocusRestore()
        assertEquals(1, blocked)

        offered = RequestActions()
        composeTestRule.waitForIdle()

        actionButton(R.string.tv_detail_block).assertDoesNotExist()
        composeTestRule.onNode(isFocused()).assertExists()
    }

    /**
     * The read-only case the board's row doesn't reach: an already-available title with no active downloads,
     * no moderation this viewer can do, and no Open in Binge to hand off to. Arrival has nothing in reading
     * order to offer focus to, so it must fall back to the content column itself rather than leaving the
     * D-pad dead.
     */
    @Test
    fun aFailedLoadLandsOnRetry() {
        var retries = 0
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Error(SeerrError.Server),
                    events = emptyFlow(),
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = { retries++ },
                            onOpenInBinge = null,
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlock = {},
                            onSetMediaStatus = { _, _, _ -> },
                            onReportIssue = { _, _ -> },
                            onDismissReport = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(string(R.string.hub_retry)) and isFocusable()).assertIsFocused()
        pressOk()
        assertEquals(1, retries)
    }

    @Test
    fun arrivalFallsBackToTheContentColumnWhenNothingElseIsFocusable() {
        val detail = requestDetail(item = requestItem(id = 2), viewerId = null)
        composeTestRule.setContent {
            BingeTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvRequestDetailScreen(
                        state = RequestDetailUiState.Ready(detail),
                        events = emptyFlow(),
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
                                onSetMediaStatus = { _, _, _ -> },
                                onReportIssue = { _, _ -> },
                                onDismissReport = {},
                            ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(isFocused()).assertExists()
    }

    private fun setContent(
        allowed: RequestActions = RequestActions(canApprove = true, canDecline = true),
        afterApprove: RequestActions? = null,
        afterDecline: RequestActions? = null,
    ) {
        offered = allowed
        val item = requestItem(status = SeerrRequestStatusCode.Pending, title = HEAT)
        val detail = requestDetail(item = item, canEdit = true, canManageUsers = true)
        composeTestRule.setContent {
            var openId by remember { mutableStateOf<Int?>(null) }
            val scope =
                ModerationScope(permissions = SeerrPermissions(canManageRequests = true), currentUserId = 7, hasBlocklist = false)
            BingeTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvRequestsRowsBoard(
                        state =
                            RequestsUiState.Ready(
                                filter = RequestFilter.All,
                                sort = RequestSort.Added,
                                counts = null,
                                scope = scope,
                                listVersion = 0,
                            ),
                        rowsFor = { filter ->
                            if (filter ==
                                RequestFilter.Pending
                            ) {
                                TvPagedRows(count = 1, at = { item })
                            } else {
                                TvPagedRows(count = 0, at = { null })
                            }
                        },
                        openRequestId = openId,
                        actions =
                            TvRequestsActions(
                                onOpenDetail = { openId = it.id },
                                onSeeAll = {},
                                onRetryLoad = {},
                                onRetryScope = {},
                            ),
                    )
                    // The overlay a real detail page would be, stacked on top exactly as the shell's overlay slot is.
                    if (openId != null) {
                        TvRequestDetailScreen(
                            state = RequestDetailUiState.Ready(detail.copy(actions = offered)),
                            events = emptyFlow(),
                            actions =
                                TvRequestDetailActions(
                                    onBack = { openId = null },
                                    onRetry = {},
                                    onOpenInBinge = null,
                                    onApprove = {
                                        approved += item.id
                                        afterApprove?.let { offered = it }
                                    },
                                    onRetryRequest = {},
                                    onDecline = {
                                        declined += it
                                        afterDecline?.let { next -> offered = next }
                                    },
                                    onRemove = { removed += it },
                                    onBlock = { blocked++ },
                                    onSetMediaStatus = { _, _, _ -> },
                                    onReportIssue = { _, _ -> },
                                    onDismissReport = {},
                                ),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun row(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    /** A resting icon button names itself by description and a labelled one by text; either way the surface is the focusable node. */
    private fun actionButton(label: Int) =
        composeTestRule.onNode(
            isFocusable() and
                (hasContentDescription(string(label)) or hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun pressOk() = press(Key.DirectionCenter)

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** The board offers focus back to the row one frame after the open request id clears. */
    private fun settleFocusRestore() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
