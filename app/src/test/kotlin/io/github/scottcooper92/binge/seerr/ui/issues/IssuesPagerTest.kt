package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.PagingData
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The issues browser as swipeable filter pages: a page composed before it is selected shows its own filter's rows. */
@RunWith(RobolectricTestRunner::class)
class IssuesPagerTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `a page swiped into view shows its own filter's rows, and reports the swipe`() {
        val selections = mutableListOf<IssueFilter>()
        composeTestRule.setContent {
            BingeExpressiveTheme {
                IssuesScreen(
                    // Held at Open: the page swiped to is composed but never selected.
                    state = IssuesUiState.Ready(filter = IssueFilter.Open, sort = IssueSort.Added, counts = null, scope = IssueListScope()),
                    issuesFor = ::rowsFor,
                    actions =
                        IssuesActions(
                            onBack = {},
                            onFilterChange = { selections += it },
                            onSortChange = {},
                            onOpen = {},
                            onRetryLoad = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(
                hasScrollToIndexAction() and
                    hasAnyDescendant(hasScrollToIndexAction() and hasAnyDescendant(hasText(titleFor(IssueFilter.Open)))),
            ).performScrollToIndex(IssueFilter.Resolved.ordinal)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(titleFor(IssueFilter.Resolved)).assertIsDisplayed()
        assertEquals(listOf(IssueFilter.Resolved), selections)
    }

    /**
     * The pages either side of the selected one are composed before any swipe, so their rows are loading
     * already. Neither is selected by being composed: nothing is reported, and only the selected page is on screen.
     */
    @Test
    fun `the filters either side are composed without a swipe, and the selection stays put`() {
        val selections = mutableListOf<IssueFilter>()
        composeTestRule.setContent {
            BingeExpressiveTheme {
                IssuesScreen(
                    state =
                        IssuesUiState.Ready(
                            filter = IssueFilter.Resolved,
                            sort = IssueSort.Added,
                            counts = null,
                            scope = IssueListScope(),
                        ),
                    issuesFor = ::rowsFor,
                    actions =
                        IssuesActions(
                            onBack = {},
                            onFilterChange = { selections += it },
                            onSortChange = {},
                            onOpen = {},
                            onRetryLoad = {},
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(titleFor(IssueFilter.Resolved)).assertIsDisplayed()
        composeTestRule.onNodeWithText(titleFor(IssueFilter.Open)).assertExists().assertIsNotDisplayed()
        composeTestRule.onNodeWithText(titleFor(IssueFilter.All)).assertExists().assertIsNotDisplayed()
        assertEquals(emptyList<IssueFilter>(), selections)
    }

    private fun rowsFor(filter: IssueFilter): Flow<PagingData<IssueItem>> = flowOf(PagingData.from(listOf(item(filter))))

    private fun titleFor(filter: IssueFilter) = "Title for ${filter.name}"

    private fun item(filter: IssueFilter) =
        IssueItem(
            id = filter.ordinal + 1,
            tmdbId = filter.ordinal + 100,
            mediaType = RequestMediaType.Movie,
            title = titleFor(filter),
            posterUrl = null,
            year = null,
            type = IssueType.Video,
            status = IssueStatus.Open,
            reportedBy = null,
            reportedById = null,
            commentCount = 0,
            createdAtMillis = null,
            updatedAtMillis = null,
            problem = null,
            problemSeason = null,
            problemEpisode = null,
        )
}
