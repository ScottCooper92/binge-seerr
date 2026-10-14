package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvMyRequests
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HEAT = "Heat"
private const val BEAR = "The Bear"
private const val SEVERANCE = "Severance"
private const val SEE_ALL = "See all"
private const val MORE_THAN_A_ROW = 45

/**
 * The requests hub under a real D-pad: down from one row to the next, a card to its detail page, and a long row's
 * see-all tile to the paged grid. Focus coming back from either is `TvRequestsHubBackTest`'s, in the real shell. The rows come
 * from plain lists decomposed the way the entry decomposes the pagers, so nothing here waits on paging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestsRowsBoardFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val opened = mutableListOf<Int>()
    private val seeAll = mutableListOf<RequestFilter>()
    private var seeAllMine = 0
    private var retries = 0
    private var scopeRetries = 0
    private var mineRetries = 0

    @Test
    fun downFromARowLandsOnTheNextRow() {
        setBoard()
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        card(HEAT).assertIsFocused()

        pressDown()

        card(BEAR).assertIsFocused()
    }

    @Test
    fun okOnACardOpensItsDetailPage() {
        setBoard()
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        pressOk()

        assertEquals(listOf(1), opened)
    }

    @Test
    fun aRowWithMoreThanARowEndsInASeeAllTileThatOpensTheGrid() {
        setBoard(pendingTotal = MORE_THAN_A_ROW)
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        // Past the one card in the row, onto the tile that closes it.
        pressRight()
        seeAllTile().assertIsFocused()
        pressOk()

        assertEquals(listOf(RequestFilter.Pending), seeAll)
    }

    @Test
    fun aRowWithAFewRequestsHasNoSeeAllTile() {
        setBoard(pendingTotal = 1)

        composeTestRule.onNode(hasContentDescription(SEE_ALL)).assertDoesNotExist()
    }

    /** The signed-in user's own requests are a last row below the filters', so Down from the last filter reaches them. */
    @Test
    fun yourRequestsAreTheLastRowAndOkOnACardOpensIt() {
        setBoard(mine = listOf(request(3, SEVERANCE, SeerrRequestStatusCode.Approved)))
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        pressDown()
        pressDown()
        card(SEVERANCE).assertIsFocused()
        pressOk()

        assertEquals(listOf(3), opened)
    }

    @Test
    fun noRequestsOfYoursLeavesNoRow() {
        setBoard(mine = emptyList())

        composeTestRule.onNode(hasText(string(R.string.tv_account_your_requests), substring = true)).assertDoesNotExist()
    }

    @Test
    fun pastTheCapYourRowEndsInASeeAllTileThatOpensYourWholeList() {
        setBoard(mine = listOf(request(3, SEVERANCE, SeerrRequestStatusCode.Approved)), mineCount = MORE_THAN_A_ROW)
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressDown()
        pressDown()
        card(SEVERANCE).assertIsFocused()
        composeTestRule.waitForIdle()

        pressRight()
        seeAllTile().assertIsFocused()
        pressOk()

        assertEquals(1, seeAllMine)
        assertEquals(emptyList<RequestFilter>(), seeAll)
    }

    @Test
    fun yourRequestsAloneStillMakeAHome() {
        setBoard(empty = true, mine = listOf(request(3, SEVERANCE, SeerrRequestStatusCode.Approved)))

        card(SEVERANCE).assertExists()
    }

    @Test
    fun aFailedLoadOffersARetry() {
        setBoard(refresh = TvLoadPhase.Failed, empty = true)

        composeTestRule.onNode(hasTextExactly(string(R.string.hub_retry)) and isFocusable()).assertExists()
    }

    /** A row whose list could not be read says so in its slot amid the others, and Down stops on its retry (#1537). */
    @Test
    fun aRowThatCouldNotBeReadSaysSoAmidTheOthersAndRetries() {
        setBoard(failedFilter = RequestFilter.Processing)
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        pressDown()
        pressDown()
        retryButton().assertIsFocused()
        composeTestRule.onNode(hasText(string(R.string.tv_list_load_failed))).assertExists()
        pressOk()

        assertEquals(1, retries)
    }

    /** Your own requests failing is not the same as having made none: the row stays, says so, and retries its own list. */
    @Test
    fun yourRequestsThatCouldNotBeReadSaySoAndRetryTheirOwnList() {
        setBoard(mineRefresh = TvLoadPhase.Failed)
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        pressDown()
        pressDown()
        retryButton().assertIsFocused()
        composeTestRule.onNode(hasText(string(R.string.tv_account_your_requests), substring = true)).assertExists()
        pressOk()

        assertEquals(1, mineRetries)
        assertEquals(0, retries)
    }

    /** The scope itself could not be read: the page's retry reads the scope again, not a row (#1054). */
    @Test
    fun aScopeThatCouldNotBeReadRetriesTheScope() {
        setBoard(scopeError = SeerrError.Unreachable)

        val retry = composeTestRule.onNode(hasTextExactly(string(R.string.hub_retry)) and isFocusable())
        retry.requestFocus()
        composeTestRule.waitForIdle()
        pressOk()

        assertEquals(1, scopeRetries)
        assertEquals(0, retries)
    }

    private fun setBoard(
        pendingTotal: Int = 1,
        refresh: TvLoadPhase = TvLoadPhase.Idle,
        empty: Boolean = false,
        scopeError: SeerrError? = null,
        mine: List<com.cooper.binge.companion.seerr.ui.requests.RequestItem> = emptyList(),
        mineCount: Int = mine.size,
        failedFilter: RequestFilter? = null,
        mineRefresh: TvLoadPhase = TvLoadPhase.Idle,
    ) {
        val pending = listOf(request(1, HEAT, SeerrRequestStatusCode.Pending))
        val approved = listOf(request(2, BEAR, SeerrRequestStatusCode.Approved))
        composeTestRule.setContent {
            val ready =
                RequestsUiState.Ready(
                    filter = RequestFilter.All,
                    sort = RequestSort.Added,
                    counts = RequestCounts(total = 2, pending = pendingTotal, approved = 1, processing = 0, available = 0),
                    scope =
                        ModerationScope(
                            permissions = SeerrPermissions(canManageRequests = true),
                            currentUserId = 7,
                            hasBlocklist = false,
                        ),
                    listVersion = 0,
                )
            val state = scopeError?.let { RequestsUiState.Error(it) } ?: ready
            BingeTvTheme {
                TvRequestsRowsBoard(
                    state = state,
                    rowsFor = { filter ->
                        val items =
                            when {
                                empty || filter == failedFilter -> emptyList()
                                filter == RequestFilter.Pending -> pending
                                filter == RequestFilter.Approved -> approved
                                else -> emptyList()
                            }
                        val phase = if (filter == failedFilter) TvLoadPhase.Failed else refresh
                        TvPagedRows(count = items.size, at = { items.getOrNull(it) }, refresh = phase)
                    },
                    mine =
                        TvMyRequests(
                            rows = TvPagedRows(count = mine.size, at = { mine.getOrNull(it) }, refresh = mineRefresh),
                            count = mineCount,
                            onSeeAll = { seeAllMine++ },
                            onRetry = { mineRetries++ },
                        ),
                    actions =
                        TvRequestsActions(
                            onOpenDetail = { item ->
                                opened += item.id
                            },
                            onSeeAll = {
                                seeAll += it
                            },
                            onRetryLoad = { retries++ },
                            onRetryScope = { scopeRetries++ },
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun card(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    private fun retryButton() = composeTestRule.onNode(hasTextExactly(string(R.string.hub_retry)) and isFocusable())

    private fun seeAllTile() = composeTestRule.onNode(hasContentDescription(SEE_ALL) and isFocusable())

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressOk() = press(Key.DirectionCenter)

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
