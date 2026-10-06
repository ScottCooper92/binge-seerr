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
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationScope
import io.github.scottcooper92.binge.seerr.ui.requests.RequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsRowsBoard
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardAndroidComposeRule
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

    /**
     * The read-only case the board's row doesn't reach: an already-available title with no active downloads,
     * no moderation this viewer can do, and no Open in Binge to hand off to. Arrival has nothing in reading
     * order to offer focus to, so it must fall back to the content column itself rather than leaving the
     * D-pad dead.
     */
    @Test
    fun arrivalFallsBackToTheContentColumnWhenNothingElseIsFocusable() {
        val item =
            RequestItem(
                id = 2,
                tmdbId = 2,
                mediaType = RequestMediaType.Movie,
                title = HEAT,
                posterUrl = null,
                year = "1995",
                requestedBy = "ana",
                requestedById = 3,
                requestedAtMillis = null,
                status = SeerrRequestStatusCode.Approved,
                mediaStatus = null,
                download = null,
                seasonNumbers = emptyList(),
                is4k = false,
            )
        val detail =
            RequestDetail(
                item = item,
                actions = RequestActions(),
                canEdit = false,
                canEditDestination = false,
                backdropUrl = null,
                overview = null,
                modifiedBy = null,
                modifiedById = null,
                viewerId = null,
                canManageUsers = false,
                updatedAtMillis = null,
                seasons = emptyList(),
                destination = null,
                downloads = emptyList(),
                mediaId = 9,
                canReportIssue = false,
                webUrl = "https://seerr.example/movie/2",
                mediaServerUrl = null,
                serviceUrl = null,
                media = null,
                siblings = emptyList(),
            )
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
                            ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(isFocused()).assertExists()
    }

    private fun setContent(allowed: RequestActions = RequestActions(canApprove = true, canDecline = true)) {
        val item =
            RequestItem(
                id = 1,
                tmdbId = 1,
                mediaType = RequestMediaType.Movie,
                title = HEAT,
                posterUrl = null,
                year = "1995",
                requestedBy = "ana",
                requestedById = 3,
                requestedAtMillis = null,
                status = SeerrRequestStatusCode.Pending,
                mediaStatus = null,
                download = null,
                seasonNumbers = emptyList(),
                is4k = false,
            )
        val detail =
            RequestDetail(
                item = item,
                actions = allowed,
                canEdit = true,
                canEditDestination = false,
                backdropUrl = null,
                overview = null,
                modifiedBy = null,
                modifiedById = null,
                viewerId = 7,
                canManageUsers = true,
                updatedAtMillis = null,
                seasons = emptyList(),
                destination = null,
                downloads = emptyList(),
                mediaId = 9,
                canReportIssue = false,
                webUrl = "https://seerr.example/movie/1",
                mediaServerUrl = null,
                serviceUrl = null,
                media = null,
                siblings = emptyList(),
            )
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
                                actingIds = emptySet(),
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
                                onReconnect = {},
                            ),
                    )
                    // The overlay a real detail page would be, stacked on top exactly as the shell's overlay slot is.
                    if (openId != null) {
                        TvRequestDetailScreen(
                            state = RequestDetailUiState.Ready(detail),
                            events = emptyFlow(),
                            actions =
                                TvRequestDetailActions(
                                    onBack = { openId = null },
                                    onRetry = {},
                                    onOpenInBinge = null,
                                    onApprove = { approved += item.id },
                                    onRetryRequest = {},
                                    onDecline = { declined += it },
                                    onRemove = { removed += it },
                                    onBlock = { blocked++ },
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
