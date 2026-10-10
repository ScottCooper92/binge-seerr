package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import io.github.scottcooper92.binge.seerr.preview.SeerrLandscapePanesPreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.hub.HubScreen
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.previewActions
import io.github.scottcooper92.binge.seerr.ui.hub.previewReady
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationScope
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import kotlinx.coroutines.flow.flowOf
import com.binge.designsystem.R as DesR

/**
 * The hub and Requests side by side in phone landscape, each pane told which edge it shares, as the app's nav host
 * tells them (#814): the gap between their content is the design system's narrow inner inset on each side of its
 * spacer, and the section's title, chips and rows start from one edge. A frame cannot draw a display cutout, so that
 * half of the fix stays with the design system's own pane-inset tests.
 */
class PaneGutterScreenshotTest {
    @PreviewTest
    @SeerrLandscapePanesPreview
    @Composable
    fun hubBesideRequests() {
        CompositionLocalProvider(LocalIsSinglePaneNav provides false) {
            Row(Modifier.fillMaxSize()) {
                PaneContent(innerEdge = PaneEdge.End, modifier = Modifier.weight(1f)) {
                    HubScreen(state = previewReady(), actions = previewActions(), selectedSection = HubSection.Requests)
                }
                Spacer(Modifier.width(dimensionResource(DesR.dimen.pane_spacer)))
                PaneContent(innerEdge = PaneEdge.Start, modifier = Modifier.weight(1f)) { Requests() }
            }
        }
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
