package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.telemetry.LocalAnalytics
import com.cooper.binge.companion.seerr.telemetry.screenName
import com.cooper.binge.companion.seerr.ui.ScopedViewModels
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SettingsViewModel
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter

/**
 * The connected television: Home, with Account, Issues and Settings opening above it from its top row, each
 * bound to the same ViewModel as its phone screen, and the edit-connection form as a full-screen overlay above them.
 */
@Composable
internal fun TvConnectedShell(hubViewModel: HubViewModel = hiltViewModel()) {
    // The hub state the Account page reads, for the signed-in user's id and whether Issues is offered.
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val account = (hub as? HubUiState.Ready)?.overview?.account
    var tab by rememberSaveable { mutableStateOf(TvDestination.Hub) }
    var sheet by rememberSaveable { mutableStateOf<TvDestination?>(null) }
    // Settings opens once its page has loaded, so the sheet never slides in on a loading line.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var settingsPending by remember { mutableStateOf(false) }
    LaunchedEffect(settingsPending, settings) {
        if (settingsPending && settings is SettingsUiState.Ready) {
            settingsPending = false
            sheet = TvDestination.Settings
        }
    }
    val analytics = LocalAnalytics.current
    LaunchedEffect(tab, sheet) { analytics.screen((sheet ?: tab).screenName()) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    // The requests board's open detail page, above the page exactly as the connection form is. The two
    // overlays are mutually exclusive by construction — nothing opens one while the other is showing.
    var openRequestId by rememberSaveable { mutableStateOf<Int?>(null) }
    // The issues board's own read-only detail page, on the same footing.
    var openIssueId by rememberSaveable { mutableStateOf<Int?>(null) }
    // A see-all grid (a filter's name), above the page. Unlike the others it stays up under a detail page opened
    // from one of its cards, so the page above and the grid below can both be showing.
    var seeAllRequests by rememberSaveable { mutableStateOf<String?>(null) }
    var seeAllIssues by rememberSaveable { mutableStateOf<String?>(null) }
    // Home's see-all for the signed-in user's own requests, which belong to no filter.
    var seeAllAccount by rememberSaveable { mutableStateOf(false) }
    val homeOverlay = openRequestId != null || seeAllRequests != null || seeAllAccount
    val issuesOverlay = openIssueId != null || seeAllIssues != null
    TvHomeShell(
        tab = tab,
        sheet = sheet,
        onSelectTab = { selected ->
            if (selected != tab) {
                tab = selected
            } else if (selected == TvDestination.Hub) {
                // The current tab again: pop it to its root, as the phone's tab reselect does.
                openRequestId = null
                seeAllRequests = null
                seeAllAccount = false
            } else {
                openIssueId = null
                seeAllIssues = null
            }
        },
        onOpenSheet = {
            if (it == TvDestination.Settings && settings !is SettingsUiState.Ready) {
                settingsViewModel.setScreenVisible(true)
                settingsPending = true
            } else {
                sheet = it
            }
        },
        showBar = !editingConnection,
        onCloseSheet = { sheet = null },
        // The phone's rule for its Issues section: shown once the server is known to let this account list them.
        showIssues = (hub as? HubUiState.Ready)?.overview?.let { HubSection.Issues.isVisible(it) } == true,
        tabsResolved = hub !is HubUiState.Loading,
        accountName = account?.name.orEmpty(),
        accountAvatarUrl = account?.avatarUrl,
        overlay =
            when {
                editingConnection ->
                    {
                        { TvEditConnectionOverlay(onDone = { editingConnection = false }) }
                    }
                tab == TvDestination.Hub && homeOverlay ->
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
                            if (seeAllAccount && account != null) {
                                TvAccountRequestsGridOverlay(
                                    accountId = account.id,
                                    detailOpen = openRequestId != null,
                                    onOpenRequest = { openRequestId = it },
                                    onDone = { seeAllAccount = false },
                                )
                            }
                            TvDetailOverlays(openRequestId, null, { openRequestId = null }, {})
                        }
                    }
                tab == TvDestination.Issues && issuesOverlay ->
                    {
                        {
                            seeAllIssues?.let { name ->
                                TvIssuesGridOverlay(
                                    filter = IssueFilter.valueOf(name),
                                    detailOpen = openIssueId != null,
                                    onOpenIssue = { openIssueId = it },
                                    onDone = { seeAllIssues = null },
                                )
                            }
                            TvDetailOverlays(null, openIssueId, {}, { openIssueId = null })
                        }
                    }
                else -> null
            },
    ) { destination, close ->
        when (destination) {
            TvDestination.Account ->
                TvAccountEntry(
                    hubViewModel = hubViewModel,
                    onDismissRequest = close,
                    onSignOut =
                        (settings as? SettingsUiState.Ready)?.takeIf { it.connection.signInKind == SignInKind.Session }?.let { ready ->
                            {
                                com.cooper.binge.companion.seerr.ui.ProtoSignOut.serverUrl = ready.connection.baseUrl
                                settingsViewModel.disconnect()
                            }
                        },
                )
            // Home is what needs attention: the requests as rows over a backdrop, once the server answers.
            TvDestination.Hub ->
                TvHomeEntry(
                    onReconnect = { editingConnection = true },
                    onOpenRequest = { openRequestId = it },
                    onSeeAll = { seeAllRequests = it.name },
                    onSeeAllMine = { seeAllAccount = true },
                )
            TvDestination.Issues ->
                TvIssuesEntry(
                    onOpenIssue = { openIssueId = it },
                    seeAllOpen = seeAllIssues != null,
                    onSeeAll = { seeAllIssues = it.name },
                )
            TvDestination.Settings -> TvSettingsEntry(onEditConnection = { editingConnection = true }, onDismissRequest = close)
        }
    }
}

/**
 * The request and issue pages above the page. Each page its own view models, cleared when it closes: a reopened page starts
 * fresh, and nothing piles up in the activity's store or outlives a change of server (#789).
 */
@Composable
private fun TvDetailOverlays(
    openRequestId: Int?,
    openIssueId: Int?,
    onCloseRequest: () -> Unit,
    onCloseIssue: () -> Unit,
) {
    openRequestId?.let { id ->
        ScopedViewModels("request-detail-$id") { TvRequestDetailOverlay(requestId = id, onDone = onCloseRequest) }
    }
    openIssueId?.let { id ->
        ScopedViewModels("issue-detail-$id") { TvIssueDetailOverlay(issueId = id, onDone = onCloseIssue) }
    }
}
