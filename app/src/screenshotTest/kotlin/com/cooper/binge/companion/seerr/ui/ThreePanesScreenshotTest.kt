package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.hub.HubScreen
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.hub.previewActions
import com.cooper.binge.companion.seerr.ui.hub.previewReady
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope
import com.cooper.binge.companion.seerr.ui.issues.IssueSort
import com.cooper.binge.companion.seerr.ui.issues.IssuesActions
import com.cooper.binge.companion.seerr.ui.issues.IssuesScreen
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailPage
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsActions
import com.cooper.binge.companion.seerr.ui.requests.RequestsScreen
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.requests.pendingDetail
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
