package io.github.scottcooper92.binge.seerr.ui.requests

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
 * The requests browser's root: the chips with the server's totals and the sort, the loading arm, and the
 * arm for a signed-in user it could not read, which is the error with its retry in place of the skeleton
 * that used to hang. The rows are paged, and `LazyPagingItems` never leaves loading in a static frame (see
 * `UserDetailScreenshotTest`), so the ready frames show the page as its skeleton; the rows themselves are
 * framed by [RequestListScreenshotTest]. The refreshing frame is the exception: its rows are replayed, so they show.
 */
class RequestsScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = Frame(ready(counts = RequestCounts(total = 128, pending = 6, approved = 90, processing = 12, available = 70)))

    /** Counts not read yet: the chips draw without a total. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readyWithoutCounts() = Frame(ready(counts = null))

    /** A pull refreshing the rows on screen: the spinner rests below the bar and the chips, and no bar sits over the rows. */
    @OptIn(ExperimentalMaterial3Api::class)
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun refreshing() {
        val rows = remember { refreshingRows(listOf(longStatusRow(), fourKLongStatusRow(), seasonListRow())) }
        Frame(ready(counts = RequestCounts(total = 128, pending = 6, approved = 90, processing = 12, available = 70)), rows, RestingPull)
    }

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(RequestsUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scopeUnreachable() = Frame(RequestsUiState.Error(SeerrError.Unreachable))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scopeUnauthorized() = Frame(RequestsUiState.Error(SeerrError.Unauthorized))
}

private fun ready(counts: RequestCounts?) =
    RequestsUiState.Ready(
        filter = RequestFilter.All,
        sort = RequestSort.Added,
        counts = counts,
        scope = ModerationScope(permissions = SeerrPermissions(canManageRequests = true), currentUserId = 1),
        listVersion = 1,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Frame(
    state: RequestsUiState,
    rows: Flow<PagingData<RequestItem>> = remember { flowOf(PagingData.from(emptyList())) },
    pullState: PullToRefreshState? = null,
) {
    RequestsScreen(
        state = state,
        requestsFor = { rows },
        pullState = pullState,
        shouldRefresh = { _, _ -> false },
        actions =
            RequestsActions(
                onBack = {},
                onFilterChange = {},
                onSortChange = {},
                onOpen = {},
                onRetryLoad = {},
                onRefreshCounts = {},
                onChanged = {},
                detailSheet = {},
            ),
    )
}
