package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueSheet
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueSheetActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesBoard
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesGrid
import com.cooper.binge.companion.seerr.ui.tv.issues.sampleIssueSheetState
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TITLE = "Severance"
private const val GRID_TITLE = "Andor"
private const val REPORT = "The English subtitles drift"

/**
 * An issue opened from the board, or from a row's see-all grid, takes focus in its sheet over the bar: the report card,
 * once the issue has loaded, rather than leaving it on the card that opened it behind the scrim. Back hands it back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssueSheetArrivalTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var sheet by mutableStateOf<IssueDetailUiState?>(null)
    private var gridOpen by mutableStateOf(false)

    @Test
    fun anIssueOpenedFromTheBoardLandsOnTheReportOnceLoaded() {
        setShell()
        card().requestFocus()
        composeTestRule.waitForIdle()

        openAndLoad()

        report().assertIsFocused()
        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()
        card().assertIsFocused()
    }

    @Test
    fun anIssueOpenedFromTheSeeAllGridLandsOnTheReportOnceLoaded() {
        gridOpen = true
        setShell()
        card(GRID_TITLE).assertIsFocused()

        openAndLoad()

        report().assertIsFocused()
        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()
        card(GRID_TITLE).assertIsFocused()
    }

    /** OK opens the sheet on its loading line, as the real entry does, and the issue then arrives. */
    private fun openAndLoad() {
        composeTestRule.press(Key.DirectionCenter)
        composeTestRule.settleShellHandOff()
        sheet = sampleIssueSheetState()
        composeTestRule.waitForIdle()
        composeTestRule.settleShellHandOff()
    }

    private fun setShell() {
        val items = listOf(issue(TITLE))
        val rows = TvPagedRows(count = items.size, at = { items.getOrNull(it) })
        val gridItems = listOf(issue(GRID_TITLE))
        val gridRows = TvPagedRows(count = gridItems.size, at = { gridItems.getOrNull(it) })
        val noRows = TvPagedRows<IssueItem>(count = 0, at = { null })
        composeTestRule.setContent {
            BingeTvTheme {
                BoardInShell(
                    destination = TvDestination.Issues,
                    overlay =
                        if (gridOpen) {
                            {
                                TvIssuesGrid(
                                    filter = IssueFilter.Open,
                                    counts = null,
                                    rows = gridRows,
                                    detailOpen = sheet != null,
                                    onOpenDetail = { sheet = IssueDetailUiState.Loading },
                                    onRetryLoad = {},
                                    onBack = { gridOpen = false },
                                )
                            }
                        } else {
                            null
                        },
                    topLayer = sheet?.let { state -> { Sheet(state) } },
                ) {
                    Box {
                        TvIssuesBoard(
                            state = ready(),
                            rowsFor = { filter -> if (filter == IssueFilter.Open) rows else noRows },
                            actions =
                                TvIssuesActions(
                                    onOpenDetail = { sheet = IssueDetailUiState.Loading },
                                    onSeeAll = {},
                                    onRetryLoad = {},
                                    onRetryScope = {},
                                ),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.settleShellHandOff()
    }

    @Composable
    private fun Sheet(state: IssueDetailUiState) =
        TvIssueSheet(
            state = state,
            actions =
                TvIssueSheetActions(
                    onDismiss = { sheet = null },
                    onRetry = {},
                    onToggleStatus = {},
                    onDeleteIssue = {},
                    onDeleteComment = {},
                ),
        )

    private fun ready() =
        IssuesUiState.Ready(
            filter = IssueFilter.Open,
            sort = IssueSort.Added,
            counts = IssueCounts(total = 1, open = 1, resolved = 0),
            scope = IssueListScope(permissions = SeerrPermissions(), currentUserId = 9),
        )

    private fun card(title: String = TITLE) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    private fun report() = composeTestRule.onNode(isFocusable() and hasText(REPORT, substring = true))

    private fun issue(title: String) =
        IssueItem(
            id = 100,
            tmdbId = 100,
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
            problem = REPORT,
            problemSeason = 1,
            problemEpisode = 4,
        )
}
