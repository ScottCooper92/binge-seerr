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
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsBoard
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
 * moved onto — approving closes the sheet back onto the same button rather than the page itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestDetailFocusTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val approved = mutableListOf<Int>()

    @Test
    fun okOnTheRowReachesThePageAndBackReturnsFocusToTheRow() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        pressOk()
        manageButton().assertIsFocused()

        pressBack()
        settleFocusRestore()

        row(HEAT).assertIsFocused()
    }

    @Test
    fun thePagesPrimaryActionOpensTheSheetAndApprovingClosesBackToTheButton() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        manageButton().assertIsFocused()

        pressOk()
        sheetRow(R.string.request_approve).assertIsFocused()
        pressOk()

        assertEquals(listOf(1), approved)
        settleFocusRestore()
        manageButton().assertIsFocused()
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
                            ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(isFocused()).assertExists()
    }

    private fun setContent() {
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
                actions = RequestActions(canApprove = true, canDecline = true),
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
                    TvRequestsBoard(
                        state =
                            RequestsUiState.Ready(
                                filter = RequestFilter.All,
                                sort = RequestSort.Added,
                                counts = null,
                                scope = scope,
                                actingIds = emptySet(),
                                listVersion = 0,
                            ),
                        rows = TvPagedRows(count = 1, at = { item }),
                        openRequestId = openId,
                        actions =
                            TvRequestsActions(
                                onFilterChange = {},
                                onSortChange = {},
                                onOpenDetail = { openId = it.id },
                                onRetryLoad = {},
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
                                    onDecline = {},
                                    onRemove = {},
                                ),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun row(title: String) = composeTestRule.onNode(hasText(title) and isFocusable())

    private fun manageButton() = composeTestRule.onNode(hasText(string(R.string.request_primary_review)) and isFocusable())

    private fun sheetRow(label: Int) = composeTestRule.onNode(hasText(string(label)) and isFocusable())

    private fun pressOk() = press(Key.DirectionCenter)

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
