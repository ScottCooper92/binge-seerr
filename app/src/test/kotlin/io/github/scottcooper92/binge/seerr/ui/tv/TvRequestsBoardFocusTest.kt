package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationScope
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val HEAT = "Heat"
private const val BEAR = "The Bear"

/**
 * The requests board under a real D-pad: the band to the rows, a row to its detail page (OK now opens the
 * page rather than the moderation sheet directly — see `TvRequestDetailFocusTest` for that round trip), and
 * the row regaining focus once the open request id clears. The rows come from a plain list decomposed the
 * way the entry decomposes the pager, so nothing here waits on paging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestsBoardFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val opened = mutableListOf<Int>()
    private var filters = mutableListOf<RequestFilter>()
    private var sorts = mutableListOf<RequestSort>()

    private val manager =
        ModerationScope(
            permissions = SeerrPermissions(canManageRequests = true, canManageBlocklist = true),
            currentUserId = 7,
            hasBlocklist = true,
        )
    private val bystander = ModerationScope(permissions = SeerrPermissions(), currentUserId = 7, hasBlocklist = false)

    @Test
    fun downFromTheBandLandsOnTheFirstRow() {
        setBoard(manager)
        focusBand()

        pressDown()

        row(HEAT).assertIsFocused()
    }

    @Test
    fun okOnAPillCommitsTheFilterAndOkOnTheChosenOneDoesNot() {
        setBoard(manager)
        focusBand()

        pressOk()
        assertTrue("re-pressing the chosen filter must not re-query", filters.isEmpty())
        pressRight()
        pressOk()

        assertEquals(listOf(RequestFilter.Pending), filters)
    }

    @Test
    fun theSortSitsAtTheEndOfTheBandAndCommitsOnOk() {
        setBoard(manager)
        focusBand()

        // Past every filter pill, onto the sort. The chosen sort is first, so one more right reaches the other.
        repeat(RequestFilter.entries.size) { pressRight() }
        pill(R.string.requests_sort_added).assertIsFocused()
        pressRight()
        pressOk()

        assertEquals(listOf(RequestSort.Modified), sorts)
        assertTrue("choosing a sort must not re-filter", filters.isEmpty())
    }

    @Test
    fun theBandComesBackFromTheSortAndTheRowsAreStillBelowIt() {
        setBoard(manager)
        focusBand()

        repeat(RequestFilter.entries.size) { pressRight() }
        pill(R.string.requests_sort_added).assertIsFocused()
        repeat(RequestFilter.entries.size) { pressLeft() }

        pill(R.string.requests_filter_all).assertIsFocused()
        pressDown()
        row(HEAT).assertIsFocused()
    }

    @Test
    fun okOnARowOpensItsDetailPage() {
        setBoard(manager)
        focusBand()
        pressDown()

        pressOk()

        assertEquals(listOf(1), opened)
    }

    @Test
    fun aRowTheViewerCannotModerateStillOpensItsDetailPage() {
        // The page is read-only detail, not moderation, so every row opens it regardless of scope.
        setBoard(bystander)
        focusBand()
        pressDown()
        row(HEAT).assertIsFocused()

        pressOk()

        assertEquals(listOf(1), opened)
    }

    @Test
    fun theRowRegainsFocusOnceTheOpenRequestClears() {
        setBoard(manager)
        focusBand()
        pressDown()
        row(HEAT).assertIsFocused()
        pressOk()
        assertEquals(listOf(1), opened)

        // The overlay above the rail owns closing itself; the board only reacts to openRequestId clearing.
        openRequestId = null
        settleFocusRestore()

        row(HEAT).assertIsFocused()
    }

    private var openRequestId: Int? by mutableStateOf(null)

    private fun setBoard(scope: ModerationScope) {
        val items = listOf(request(1, HEAT), request(2, BEAR))
        composeTestRule.setContent {
            val state =
                RequestsUiState.Ready(
                    filter = RequestFilter.All,
                    sort = RequestSort.Added,
                    counts = null,
                    scope = scope,
                    actingIds = emptySet(),
                    listVersion = 0,
                )
            BingeTvTheme {
                TvRequestsBoard(
                    state = state,
                    rows = TvPagedRows(count = items.size, at = { items.getOrNull(it) }),
                    openRequestId = openRequestId,
                    actions =
                        TvRequestsActions(
                            onFilterChange = { filters += it },
                            onSortChange = { sorts += it },
                            onOpenDetail = { item ->
                                opened += item.id
                                openRequestId = item.id
                            },
                            onRetryLoad = {},
                            onReconnect = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun focusBand() {
        pill(R.string.requests_filter_all).requestFocus()
        composeTestRule.waitForIdle()
        pill(R.string.requests_filter_all).assertIsFocused()
    }

    private fun pill(label: Int) = composeTestRule.onNode(hasTextExactly(string(label)) and isFocusable())

    private fun row(title: String) = composeTestRule.onNode(hasText(title) and isFocusable())

    private fun pressDown() = press(Key.DirectionDown)

    private fun pressLeft() = press(Key.DirectionLeft)

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressOk() = press(Key.DirectionCenter)

    /** The board offers focus back one frame after the open request id clears, as the old sheet closer did. */
    private fun settleFocusRestore() {
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun request(
        id: Int,
        title: String,
    ) = RequestItem(
        id = id,
        tmdbId = id,
        mediaType = RequestMediaType.Movie,
        title = title,
        posterUrl = null,
        year = "2023",
        requestedBy = "ana",
        requestedById = 3,
        requestedAtMillis = null,
        status = SeerrRequestStatusCode.Pending,
        mediaStatus = null,
        download = null,
        seasonNumbers = emptyList(),
        is4k = false,
    )
}
