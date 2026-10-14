package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.RequestRowFilters
import com.cooper.binge.companion.seerr.ui.tv.requests.TvMyRequests
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.requestItem
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CARDS_PER_ROW = 4
private const val MORE_THAN_A_ROW = 45
private const val SEE_ALL = "See all"

/** The card opened in the Shield repro: the third card of the third row, so there are rows and cards before it. */
private const val OPENED_ROW = 2
private const val OPENED_COLUMN = 2

/** The see-all tile opened here ends the second row, so there is a row above it too. */
private const val SEE_ALL_ROW = 1

/**
 * Back from what a hub card or tile opened lands on that card or tile, on any row (#1509). The board sits in the real
 * shell beside the rail, and the page or grid is the shell's overlay: it takes focus while it shows and closes on
 * Back, as the real ones do. On the Shield, focus came back to the row above: the board's own restore landed the card,
 * then the shell's hand-off entered the destination again and lost it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestsHubBackTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var openRequestId: Int? by mutableStateOf(null)
    private var seeAllOpen by mutableStateOf(false)
    private val opened = mutableListOf<Int>()
    private val seeAll = mutableListOf<RequestFilter>()
    private var seeAllMine = 0

    @Test
    fun backFromACardBelowTheFirstRowLandsOnThatCardAndASecondBackOnTheHomeTab() {
        setBoard()
        enterAt(OPENED_ROW, OPENED_COLUMN)
        composeTestRule.press(Key.DirectionCenter)
        assertEquals(listOf(idOf(OPENED_ROW, OPENED_COLUMN)), opened)
        composeTestRule.onNodeWithTag(OVERLAY_TAG).assertIsFocused()

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        card(OPENED_ROW, OPENED_COLUMN).assertIsFocused()

        composeTestRule.pressBack()
        homeTab().assertIsFocused()
    }

    @Test
    fun backFromASeeAllGridBelowTheFirstRowLandsOnItsTileAndASecondBackOnTheHomeTab() {
        setBoard(longRow = RequestRowFilters[SEE_ALL_ROW])
        enterAt(SEE_ALL_ROW, CARDS_PER_ROW - 1)
        composeTestRule.press(Key.DirectionRight)
        seeAllTile().assertIsFocused()
        composeTestRule.press(Key.DirectionCenter)
        assertEquals(listOf(RequestRowFilters[SEE_ALL_ROW]), seeAll)
        composeTestRule.onNodeWithTag(OVERLAY_TAG).assertIsFocused()

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        seeAllTile().assertIsFocused()

        composeTestRule.pressBack()
        homeTab().assertIsFocused()
    }

    /** The signed-in user's own row ends Home, so its see-all is the lowest tile there (#1524). */
    @Test
    fun backFromMyRequestsSeeAllLandsOnItsTileAndASecondBackOnTheHomeTab() {
        setBoard(mineLong = true)
        enterAt(RequestRowFilters.size, CARDS_PER_ROW - 1)
        composeTestRule.press(Key.DirectionRight)
        seeAllTile().assertIsFocused()
        composeTestRule.press(Key.DirectionCenter)
        assertEquals(1, seeAllMine)
        composeTestRule.onNodeWithTag(OVERLAY_TAG).assertIsFocused()

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()
        seeAllTile().assertIsFocused()

        composeTestRule.pressBack()
        homeTab().assertIsFocused()
    }

    private fun setBoard(
        longRow: RequestFilter? = null,
        mineLong: Boolean = false,
    ) {
        fun countFor(filter: RequestFilter) = if (filter == longRow) MORE_THAN_A_ROW else CARDS_PER_ROW
        composeTestRule.setContent {
            val ready =
                RequestsUiState.Ready(
                    filter = RequestFilter.All,
                    sort = RequestSort.Added,
                    counts =
                        RequestCounts(
                            total = 0,
                            pending = countFor(RequestFilter.Pending),
                            approved = countFor(RequestFilter.Approved),
                            processing = countFor(RequestFilter.Processing),
                            available = countFor(RequestFilter.Available),
                        ),
                    scope =
                        ModerationScope(
                            permissions = SeerrPermissions(canManageRequests = true),
                            currentUserId = 7,
                            hasBlocklist = false,
                        ),
                    listVersion = 0,
                )
            BingeTvTheme {
                val mine = myRequests(count = if (mineLong) MORE_THAN_A_ROW else CARDS_PER_ROW)
                ShellWithOverlay(
                    destination = TvDestination.Hub,
                    overlayOpen = openRequestId != null || seeAllOpen,
                    onCloseOverlay = {
                        openRequestId = null
                        seeAllOpen = false
                    },
                ) {
                    TvRequestsRowsBoard(
                        state = ready,
                        mine = mine,
                        rowsFor = { filter ->
                            val row = RequestRowFilters.indexOf(filter)
                            val items = List(CARDS_PER_ROW) { column -> requestItem(id = idOf(row, column), title = titleOf(row, column)) }
                            TvPagedRows(count = items.size, at = { items.getOrNull(it) })
                        },
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
                                onRetryLoad = {},
                                onRetryScope = {},
                            ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /** Focus on the first card, then down and across to [row] and [column] by D-pad, as a viewer gets there. */
    private fun enterAt(
        row: Int,
        column: Int,
    ) {
        card(0, 0).requestFocus()
        composeTestRule.waitForIdle()
        repeat(row) { composeTestRule.press(Key.DirectionDown) }
        repeat(column) { composeTestRule.press(Key.DirectionRight) }
        card(row, column).assertIsFocused()
    }

    private fun card(
        row: Int,
        column: Int,
    ) = composeTestRule.onNode(hasContentDescription(titleOf(row, column)) and isFocusable())

    private fun seeAllTile() = composeTestRule.onNode(hasContentDescription(SEE_ALL) and isFocusable())

    /** The signed-in user's own row, one row's worth of cards, with [count] behind its see-all. */
    private fun myRequests(count: Int): TvMyRequests {
        val row = RequestRowFilters.size
        val items = List(CARDS_PER_ROW) { column -> requestItem(id = idOf(row, column), title = titleOf(row, column)) }
        return TvMyRequests(
            rows = TvPagedRows(count = items.size, at = { items.getOrNull(it) }),
            count = count,
            onSeeAll = {
                seeAllMine++
                seeAllOpen = true
            },
        )
    }

    private fun homeTab() = composeTestRule.onNode(hasContentDescription(string(R.string.tv_rail_hub)))
}

private fun idOf(
    row: Int,
    column: Int,
) = (row + 1) * 100 + column

private fun titleOf(
    row: Int,
    column: Int,
) = "Row $row card $column"
