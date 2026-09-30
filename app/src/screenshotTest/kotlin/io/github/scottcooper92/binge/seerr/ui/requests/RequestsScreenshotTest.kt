package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The requests browser's root: the chips with the server's totals and the sort, the loading arm, and the
 * arm for a signed-in user it could not read, which is the error with its retry in place of the skeleton
 * that used to hang. The rows are paged, and `LazyPagingItems` never leaves loading in a static frame (see
 * `UserDetailScreenshotTest`), so the ready frames show the page as its skeleton; the rows themselves are
 * framed by [RequestListScreenshotTest].
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
        actingIds = emptySet(),
        listVersion = 1,
    )

@Composable
private fun Frame(state: RequestsUiState) {
    RequestsScreen(
        state = state,
        requestsFor = { flowOf(PagingData.from(emptyList())) },
        events = emptyFlow(),
        shouldRefresh = { _, _ -> false },
        actions =
            RequestsActions(
                onBack = {},
                onFilterChange = {},
                onSortChange = {},
                onOpen = {},
                onOpenActions = {},
                onDismissActions = {},
                onRetryLoad = {},
                onApprove = {},
                onRetry = {},
                onDecline = { _, _ -> },
                onRemove = { _, _ -> },
            ),
    )
}
