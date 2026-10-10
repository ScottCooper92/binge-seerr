package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import kotlinx.coroutines.flow.flowOf

/**
 * The issues browser's root: its chrome, the chips with the server's totals and the sort. The rows are
 * paged, and `LazyPagingItems` never leaves loading in a static frame (see `UserDetailScreenshotTest`),
 * so every frame here shows the page as its skeleton; the rows themselves are framed by
 * [IssueListScreenshotTest].
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

@Composable
private fun Frame(state: IssuesUiState) {
    IssuesScreen(
        state = state,
        issuesFor = { flowOf(PagingData.from(emptyList())) },
        actions = IssuesActions(onBack = {}, onFilterChange = {}, onSortChange = {}, onOpen = {}, onRetryLoad = {}),
    )
}
