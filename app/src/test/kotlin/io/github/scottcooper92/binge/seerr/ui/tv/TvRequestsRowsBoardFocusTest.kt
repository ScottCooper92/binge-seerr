package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isFocusable
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
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsRowsBoard
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val HEAT = "Heat"
private const val BEAR = "The Bear"
private const val SEE_ALL = "See all"
private const val MORE_THAN_A_ROW = 45

/**
 * The requests hub under a real D-pad: down from one row to the next, a card to its detail page and focus coming
 * back to it, and a long row's see-all tile to the paged grid and focus coming back to the tile. The rows come
 * from plain lists decomposed the way the entry decomposes the pagers, so nothing here waits on paging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestsRowsBoardFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val opened = mutableListOf<Int>()
    private val seeAll = mutableListOf<RequestFilter>()
    private var openRequestId: Int? by mutableStateOf(null)
    private var seeAllOpen by mutableStateOf(false)
    private var retries = 0
    private var scopeRetries = 0

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
    fun theCardRegainsFocusOnceTheOpenRequestClears() {
        setBoard()
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        assertEquals(listOf(1), opened)

        // The overlay above the rail owns closing itself; the board only reacts to the open id clearing.
        openRequestId = null
        settleFocusRestore()

        card(HEAT).assertIsFocused()
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
    fun theSeeAllTileRegainsFocusOnceTheGridCloses() {
        setBoard(pendingTotal = MORE_THAN_A_ROW)
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressRight()
        pressOk()
        assertEquals(listOf(RequestFilter.Pending), seeAll)

        seeAllOpen = false
        settleFocusRestore()

        seeAllTile().assertIsFocused()
    }

    @Test
    fun aRowWithAFewRequestsHasNoSeeAllTile() {
        setBoard(pendingTotal = 1)

        composeTestRule.onNode(hasContentDescription(SEE_ALL)).assertDoesNotExist()
    }

    @Test
    fun aFailedLoadOffersARetry() {
        setBoard(refresh = TvLoadPhase.Failed, empty = true)

        composeTestRule.onNode(hasTextExactly(string(R.string.hub_retry)) and isFocusable()).assertExists()
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
                                empty -> emptyList()
                                filter == RequestFilter.Pending -> pending
                                filter == RequestFilter.Approved -> approved
                                else -> emptyList()
                            }
                        TvPagedRows(count = items.size, at = { items.getOrNull(it) }, refresh = refresh)
                    },
                    openRequestId = openRequestId,
                    seeAllOpen = seeAllOpen,
                    actions =
                        TvRequestsActions(
                            onOpenDetail = { item ->
                                opened += item.id
                                openRequestId = item.id
                            },
                            onSeeAll = {
                                seeAll += it
                                seeAllOpen = true
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

    private fun seeAllTile() = composeTestRule.onNode(hasContentDescription(SEE_ALL) and isFocusable())

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressOk() = press(Key.DirectionCenter)

    /** The board offers focus back one frame after what opened above the rail closes. */
    private fun settleFocusRestore() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(res: Int) = RuntimeEnvironment.getApplication().getString(res)
}
