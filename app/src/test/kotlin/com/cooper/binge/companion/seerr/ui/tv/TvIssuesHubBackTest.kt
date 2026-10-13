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
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.tv.issues.IssueRowFilters
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CARDS_PER_ROW = 4
private const val MORE_THAN_A_ROW = 45
private const val SEE_ALL = "See all"

/** The Resolved row, under Open: the row below the first, where the requests hub lost the opened card (#1509). */
private const val LOWER_ROW = 1
private const val OPENED_COLUMN = 2

/**
 * The issues hub's half of #1509: Back from the issue page, or from a row's see-all grid, lands on the card or tile that
 * opened it, below the first row. The viewer may manage nothing, so OK on a card opens the read-only page directly
 * rather than the board's own sheet. The board sits in the real shell, and the page is the shell's overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssuesHubBackTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var openIssueId: Int? by mutableStateOf(null)
    private var seeAllOpen by mutableStateOf(false)
    private val opened = mutableListOf<Int>()
    private val seeAll = mutableListOf<IssueFilter>()

    @Test
    fun backFromAnIssueBelowTheFirstRowLandsOnItsCard() {
        setBoard()
        enterAt(LOWER_ROW, OPENED_COLUMN)
        composeTestRule.press(Key.DirectionCenter)
        assertEquals(listOf(idOf(LOWER_ROW, OPENED_COLUMN)), opened)
        composeTestRule.onNodeWithTag(OVERLAY_TAG).assertIsFocused()

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        card(LOWER_ROW, OPENED_COLUMN).assertIsFocused()
    }

    @Test
    fun backFromASeeAllGridBelowTheFirstRowLandsOnItsTile() {
        setBoard(longRow = IssueRowFilters[LOWER_ROW])
        enterAt(LOWER_ROW, CARDS_PER_ROW - 1)
        composeTestRule.press(Key.DirectionRight)
        seeAllTile().assertIsFocused()
        composeTestRule.press(Key.DirectionCenter)
        assertEquals(listOf(IssueRowFilters[LOWER_ROW]), seeAll)
        composeTestRule.onNodeWithTag(OVERLAY_TAG).assertIsFocused()

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        seeAllTile().assertIsFocused()
    }

    private fun setBoard(longRow: IssueFilter? = null) {
        fun countFor(filter: IssueFilter) = if (filter == longRow) MORE_THAN_A_ROW else CARDS_PER_ROW
        composeTestRule.setContent {
            val ready =
                IssuesUiState.Ready(
                    filter = IssueFilter.Open,
                    sort = IssueSort.Added,
                    counts = IssueCounts(total = 0, open = countFor(IssueFilter.Open), resolved = countFor(IssueFilter.Resolved)),
                    scope = IssueListScope(permissions = SeerrPermissions(), currentUserId = 9),
                )
            BingeTvTheme {
                ShellWithOverlay(
                    destination = TvDestination.Issues,
                    overlayOpen = openIssueId != null || seeAllOpen,
                    onCloseOverlay = {
                        openIssueId = null
                        seeAllOpen = false
                    },
                ) {
                    TvIssuesBoard(
                        state = ready,
                        rowsFor = { filter ->
                            val row = IssueRowFilters.indexOf(filter)
                            val items = List(CARDS_PER_ROW) { column -> issue(idOf(row, column), titleOf(row, column)) }
                            TvPagedRows(count = items.size, at = { items.getOrNull(it) })
                        },
                        events = emptyFlow(),
                        seeAllOpen = seeAllOpen,
                        actions =
                            TvIssuesActions(
                                onOpenActions = {},
                                onDismissActions = {},
                                onOpenDetail = { item ->
                                    opened += item.id
                                    openIssueId = item.id
                                },
                                onResolve = {},
                                onReopen = {},
                                onDelete = {},
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
}

private fun issue(
    id: Int,
    title: String,
) = IssueItem(
    id = id,
    tmdbId = id,
    mediaType = RequestMediaType.Tv,
    title = title,
    posterUrl = null,
    year = "2022",
    type = IssueType.Subtitles,
    status = IssueStatus.Open,
    reportedBy = "ana",
    reportedById = 3,
    commentCount = 0,
    createdAtMillis = null,
    updatedAtMillis = null,
    problem = "The subtitles run late.",
    problemSeason = 1,
    problemEpisode = 4,
)

private fun idOf(
    row: Int,
    column: Int,
) = (row + 1) * 100 + column

private fun titleOf(
    row: Int,
    column: Int,
) = "Issue row $row card $column"
