package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.state.RestingPull
import io.github.scottcooper92.binge.seerr.ui.state.refreshingRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The issues browser's root: its chrome, the chips with the server's totals and the sort. The rows are
 * paged, and `LazyPagingItems` never leaves loading in a static frame (see `UserDetailScreenshotTest`),
 * so the frames here show the page as its skeleton; the rows themselves are framed by
 * [IssueListScreenshotTest]. The refreshing frame is the exception: its rows are replayed, so they show.
 */
class IssuesRootScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = Frame(IssuesUiState.Ready(IssueFilter.Open, IssueSort.Added, IssueCounts(12, 5, 7), moderatorScope()))

    /** Counts not read yet: the chips draw without a total. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readyWithoutCounts() = Frame(IssuesUiState.Ready(IssueFilter.All, IssueSort.Modified, null, moderatorScope()))

    /** A pull refreshing the rows on screen: the spinner rests below the bar and the chips, and no bar sits over the rows. */
    @OptIn(ExperimentalMaterial3Api::class)
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun refreshing() {
        val rows = remember { refreshingRows(listOf(crowdedEpisodeIssueRow(), crowdedSeasonIssueRow())) }
        Frame(IssuesUiState.Ready(IssueFilter.Open, IssueSort.Added, IssueCounts(12, 5, 7), moderatorScope()), rows, RestingPull)
    }

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(IssuesUiState.Loading)

    /** The signed-in user could not be read: the page says so, with a retry, rather than an empty list. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scopeUnreachable() = Frame(IssuesUiState.Error(SeerrError.Unreachable))
}

private fun moderatorScope() = IssueListScope(permissions = SeerrPermissions(canManageIssues = true), currentUserId = 1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Frame(
    state: IssuesUiState,
    rows: Flow<PagingData<IssueItem>> = remember { flowOf(PagingData.from(emptyList())) },
    pullState: PullToRefreshState? = null,
) {
    IssuesScreen(
        state = state,
        issuesFor = { rows },
        pullState = pullState,
        actions = IssuesActions(onBack = {}, onFilterChange = {}, onSortChange = {}, onOpen = {}, onRetryLoad = {}, onRefreshCounts = {}),
    )
}
