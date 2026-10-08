package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.telemetry.LocalAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.screenName
import io.github.scottcooper92.binge.seerr.ui.ScopedViewModels
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter

/**
 * The connected television: the rail with a board per destination, each bound to the same ViewModel as
 * its phone screen, and the edit-connection form as a full-screen overlay above the rail.
 */
@Composable
internal fun TvConnectedShell(hubViewModel: HubViewModel = hiltViewModel()) {
    // The account's avatar on the rail's top item, from the same hub state the Account page reads.
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val account = (hub as? HubUiState.Ready)?.overview?.account
    var selected by rememberSaveable { mutableStateOf(TvDestination.Hub) }
    val analytics = LocalAnalytics.current
    LaunchedEffect(selected) { analytics.screen(selected.screenName()) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    // The requests board's open detail page, above the rail exactly as the connection form is. The two
    // overlays are mutually exclusive by construction — nothing opens one while the other is showing.
    var openRequestId by rememberSaveable { mutableStateOf<Int?>(null) }
    // The issues board's own read-only detail page, on the same footing.
    var openIssueId by rememberSaveable { mutableStateOf<Int?>(null) }
    // A see-all grid (a filter's name), above the rail. Unlike the others it stays up under a detail page opened
    // from one of its cards, so the page above and the grid below can both be showing.
    var seeAllRequests by rememberSaveable { mutableStateOf<String?>(null) }
    var seeAllIssues by rememberSaveable { mutableStateOf<String?>(null) }
    TvShellScaffold(
        selected = selected,
        onSelect = { selected = it },
        accountName = account?.name,
        accountAvatarUrl = account?.avatarUrl,
        // The phone's rule for its Issues section: shown once the server is known to let this account list them.
        showIssues = (hub as? HubUiState.Ready)?.overview?.let { HubSection.Issues.isVisible(it) } == true,
        overlay =
            when {
                editingConnection ->
                    {
                        { TvEditConnectionOverlay(onDone = { editingConnection = false }) }
                    }
                openRequestId != null || openIssueId != null || seeAllRequests != null || seeAllIssues != null ->
                    {
                        {
                            seeAllRequests?.let { name ->
                                TvRequestsGridOverlay(
                                    filter = RequestFilter.valueOf(name),
                                    detailOpen = openRequestId != null,
                                    onOpenRequest = { openRequestId = it },
                                    onDone = { seeAllRequests = null },
                                )
                            }
                            seeAllIssues?.let { name ->
                                TvIssuesGridOverlay(
                                    filter = IssueFilter.valueOf(name),
                                    detailOpen = openIssueId != null,
                                    onOpenIssue = { openIssueId = it },
                                    onDone = { seeAllIssues = null },
                                )
                            }
                            // Each page its own view models, cleared when it closes: a reopened page starts fresh, and nothing
                            // piles up in the activity's store or outlives a change of server (#789).
                            openRequestId?.let { id ->
                                ScopedViewModels("request-detail-$id") {
                                    TvRequestDetailOverlay(requestId = id, onDone = {
                                        openRequestId =
                                            null
                                    })
                                }
                            }
                            openIssueId?.let { id ->
                                ScopedViewModels("issue-detail-$id") { TvIssueDetailOverlay(issueId = id, onDone = { openIssueId = null }) }
                            }
                        }
                    }
                else -> null
            },
    ) { destination ->
        when (destination) {
            TvDestination.Account ->
                TvAccountEntry(openRequestId = openRequestId, onOpenRequest = { openRequestId = it }, hubViewModel = hubViewModel)
            // Home is what needs attention: the requests as rows over a backdrop, once the server answers.
            TvDestination.Hub ->
                TvHomeEntry(
                    onReconnect = { editingConnection = true },
                    openRequestId = openRequestId,
                    onOpenRequest = { openRequestId = it },
                    seeAllOpen = seeAllRequests != null,
                    onSeeAll = { seeAllRequests = it.name },
                )
            TvDestination.Issues ->
                TvIssuesEntry(
                    openIssueId = openIssueId,
                    onOpenIssue = { openIssueId = it },
                    seeAllOpen = seeAllIssues != null,
                    onSeeAll = { seeAllIssues = it.name },
                )
            TvDestination.Settings -> TvSettingsEntry(onEditConnection = { editingConnection = true })
        }
    }
}
