package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import io.github.scottcooper92.binge.seerr.preview.SeerrTabletPanesPreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.hub.HubScreen
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.previewActions
import io.github.scottcooper92.binge.seerr.ui.hub.previewReady
import io.github.scottcooper92.binge.seerr.ui.issues.IssueCounts
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListScope
import io.github.scottcooper92.binge.seerr.ui.issues.IssueSort
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesActions
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesScreen
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationScope
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailPage
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.requests.pendingDetail
import kotlinx.coroutines.flow.flowOf

/**
 * A landscape tablet's three panes (#1110): the hub, the open section and what it opened, laid out by the scene's own
 * [ThreePaneRow] with each pane told its edges and depth as the scene tells them. The section and the item beside it
 * show no Back arrow, because neither leaves the screen.
 */
class ThreePanesScreenshotTest {
    /** The hub alone: the default section beside it, and nothing open yet. */
    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun hubWithDefaultSection() = Panes(HubSection.Requests, section = { Requests() }, item = null)

    /** A section opened from the hub, with nothing of its own open. */
    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun sectionNothingOpen() = Panes(HubSection.Issues, section = { Issues() }, item = null)

    /** A request opened from the list, beside it: no Back arrow on either. */
    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun sectionWithItemOpen() =
        Panes(HubSection.Requests, section = { Requests() }) {
            RequestDetailPage(
                detail = pendingDetail(),
                onBack = {},
                onOpen = {},
                onReport = {},
                onOpenRequest = {},
                onOpenUser = {},
                initiallyOverflowing = true,
            )
        }
}

@Composable
private fun Panes(
    selected: HubSection,
    section: @Composable () -> Unit,
    item: (@Composable () -> Unit)?,
) {
    CompositionLocalProvider(LocalIsSinglePaneNav provides false, LocalPaneDepth provides 1) {
        ThreePaneRow(
            hub = {
                PaneContent(innerEdge = PaneEdge.End) {
                    HubScreen(state = previewReady(), actions = previewActions(), selectedSection = selected)
                }
            },
            section = { DetailPaneContent(section) },
            item = { item?.let { DetailPaneContent(it) } ?: NothingOpen() },
        )
    }
}

@Composable
private fun Requests() =
    RequestsScreen(
        state =
            RequestsUiState.Ready(
                filter = RequestFilter.All,
                sort = RequestSort.Added,
                counts = RequestCounts(total = 128, pending = 6, approved = 90, processing = 12, available = 70),
                scope = ModerationScope(permissions = SeerrPermissions(canManageRequests = true), currentUserId = 1),
                listVersion = 1,
            ),
        requestsFor = { flowOf(PagingData.from(emptyList())) },
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
        showBack = false,
    )

@Composable
private fun Issues() =
    IssuesScreen(
        state =
            IssuesUiState.Ready(
                IssueFilter.Open,
                IssueSort.Added,
                IssueCounts(12, 5, 7),
                IssueListScope(permissions = SeerrPermissions(canManageIssues = true), currentUserId = 1),
            ),
        issuesFor = { flowOf(PagingData.from(emptyList())) },
        actions = IssuesActions(onBack = {}, onFilterChange = {}, onSortChange = {}, onOpen = {}, onRetryLoad = {}, onRefreshCounts = {}),
        showBack = false,
    )
