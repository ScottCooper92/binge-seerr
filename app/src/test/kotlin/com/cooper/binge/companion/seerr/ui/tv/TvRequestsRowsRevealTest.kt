package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocusable
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HEAT = "Heat"
private const val BEAR = "The Bear"

/**
 * Home's first reveal: rows that arrive one by one would lay out above the card focus had already landed on, so the
 * board holds its loading page until every row has items or has finished loading, then keeps showing rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestsRowsRevealTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private var pendingItems by mutableStateOf(listOf(request(1, HEAT, SeerrRequestStatusCode.Pending)))
    private var pendingRefresh by mutableStateOf<TvLoadPhase>(TvLoadPhase.Idle)
    private var approvedItems by mutableStateOf(emptyList<com.cooper.binge.companion.seerr.ui.requests.RequestItem>())
    private var approvedRefresh by mutableStateOf<TvLoadPhase>(TvLoadPhase.Loading)

    @Test
    fun aRowStillLoadingWithNothingHoldsTheLoadingPageForTheOthers() {
        setBoard()

        card(HEAT).assertDoesNotExist()
    }

    @Test
    fun theRowsShowOnceTheLastRowArrives() {
        setBoard()

        approvedItems = listOf(request(2, BEAR, SeerrRequestStatusCode.Approved))
        approvedRefresh = TvLoadPhase.Idle
        settle()

        card(HEAT).assertExists()
        card(BEAR).assertExists()
    }

    @Test
    fun aRowThatFinishesEmptyOrFailedCountsAsSettled() {
        setBoard()

        approvedRefresh = TvLoadPhase.Failed
        settle()

        card(HEAT).assertExists()
    }

    @Test
    fun cachedRowsShowAtOnceWhileTheirRefreshRuns() {
        approvedItems = listOf(request(2, BEAR, SeerrRequestStatusCode.Approved))
        pendingRefresh = TvLoadPhase.Loading
        setBoard()

        card(HEAT).assertExists()
        card(BEAR).assertExists()
    }

    @Test
    fun aRowThatNeverAnswersIsGivenUpOnAfterTheTimeout() {
        setBoard()
        card(HEAT).assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(TV_ROWS_REVEAL_TIMEOUT_MS + 100)
        settle()

        card(HEAT).assertExists()
    }

    @Test
    fun onceShownALaterRefreshDoesNotBringTheLoadingPageBack() {
        setBoard()
        approvedRefresh = TvLoadPhase.Idle
        settle()
        card(HEAT).assertExists()

        approvedRefresh = TvLoadPhase.Loading
        settle()

        card(HEAT).assertExists()
    }

    private fun setBoard() {
        // The timeout is the only thing the clock may not run ahead of: it is advanced by hand.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestsRowsBoard(
                    state =
                        RequestsUiState.Ready(
                            filter = RequestFilter.All,
                            sort = RequestSort.Added,
                            counts = RequestCounts(total = 2, pending = 1, approved = 1, processing = 0, available = 0),
                            scope =
                                ModerationScope(
                                    permissions = SeerrPermissions(canManageRequests = true),
                                    currentUserId = 7,
                                    hasBlocklist = false,
                                ),
                            listVersion = 0,
                        ),
                    rowsFor = { filter ->
                        when (filter) {
                            RequestFilter.Pending ->
                                TvPagedRows(count = pendingItems.size, at = { pendingItems.getOrNull(it) }, refresh = pendingRefresh)
                            RequestFilter.Approved ->
                                TvPagedRows(count = approvedItems.size, at = { approvedItems.getOrNull(it) }, refresh = approvedRefresh)
                            else -> TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Idle)
                        }
                    },
                    actions = TvRequestsActions(onOpenDetail = {}, onSeeAll = {}, onRetryLoad = {}, onRetryScope = {}),
                )
            }
        }
        settle()
    }

    // The clock is manual, so a state change needs a frame to recompose.
    private fun settle() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun card(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())
}
