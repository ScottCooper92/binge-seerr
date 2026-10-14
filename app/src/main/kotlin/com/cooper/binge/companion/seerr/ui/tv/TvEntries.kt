package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.telemetry.LocalAnalytics
import com.cooper.binge.companion.seerr.telemetry.screenName
import com.cooper.binge.companion.seerr.ui.ScopedViewModels
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SettingsViewModel
import com.cooper.binge.companion.seerr.ui.settings.SignInKind

/**
 * The connected television: Home and Issues as tabs under the top bar, with Account and Settings opening as sheets over them,
 * each bound to the same ViewModel as its phone screen, and the edit-connection form as a full-screen overlay above them.
 */
@Composable
internal fun TvConnectedShell(
    hubViewModel: HubViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    // The hub state the Account page reads, for the signed-in user's id and which tabs are offered.
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val account = (hub as? HubUiState.Ready)?.overview?.account
    val pages = rememberTvOpenPages()
    val tabsResolved = hub !is HubUiState.Loading
    // Until the hub answers, the bar is hidden and every tab stands, so a tab kept across a rotation is not dropped early.
    val tabs = if (tabsResolved) offeredTabs(hub) else TabRules.map { it.first }
    var chosenTab by rememberSaveable { mutableStateOf(TvDestination.Hub) }
    // A tab withdrawn under the user (a permission change, a reconnect as someone else) hands over to the start tab at once;
    // the effect then drops its pages and settles the choice.
    val tab = chosenTab.takeIf { it in tabs } ?: tabs.first()
    LaunchedEffect(tabs) { chosenTab = pages.reconcile(chosenTab, tabs) }
    var sheet by rememberSaveable { mutableStateOf<TvDestination?>(null) }
    val openSheet = rememberSheetOpener(settingsViewModel) { sheet = it }
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val analytics = LocalAnalytics.current
    LaunchedEffect(tab, sheet) { analytics.screen((sheet ?: tab).screenName()) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    TvHomeShell(
        tab = tab,
        sheet = sheet,
        onSelectTab = { selected -> if (selected == tab) pages.clear(tab) else chosenTab = selected },
        onOpenSheet = openSheet,
        onCloseSheet = { sheet = null },
        tabs = tabs,
        tabsResolved = tabsResolved,
        account = TvTopBarAccount(name = account?.name.orEmpty(), avatarUrl = account?.avatarUrl),
        showBar = !editingConnection,
        overlay =
            when {
                editingConnection -> {
                    { TvEditConnectionOverlay(onDone = { editingConnection = false }) }
                }
                !pages.anyOpen(tab) -> null
                else -> tabPages(tab, pages, seeAllAccountId = account?.id)
            },
    ) { destination, close ->
        when (destination) {
            TvDestination.Account ->
                TvAccountEntry(
                    hubViewModel = hubViewModel,
                    onDismissRequest = close,
                    // Only a session sign-in has a session to end; a server's API key has nothing to sign out of.
                    onSignOut = settingsViewModel::signOut.takeIf { settings.signsInWithSession() },
                )
            // Home is what needs attention: the requests as rows over a backdrop, once the server answers.
            TvDestination.Hub ->
                TvHomeEntry(
                    onReconnect = { editingConnection = true },
                    onOpenRequest = { pages.openRequestId = it },
                    onSeeAll = { pages.seeAllRequests = it.name },
                    onSeeAllMine = { pages.seeAllAccount = true },
                )
            TvDestination.Issues ->
                TvIssuesEntry(
                    onOpenIssue = { pages.openIssueId = it },
                    seeAllOpen = pages.seeAllIssues != null,
                    onSeeAll = { pages.seeAllIssues = it.name },
                )
            TvDestination.Settings -> TvSettingsEntry(onDismissRequest = close, viewModel = settingsViewModel)
        }
    }
}

private fun SettingsUiState.signsInWithSession(): Boolean = (this as? SettingsUiState.Ready)?.connection?.signInKind == SignInKind.Session

/**
 * Opens a sheet. Settings is held back until its state is ready, so it opens on its page rather than a loading line; its
 * state is collected from the shell's start, so that is rarely a wait.
 */
@Composable
private fun rememberSheetOpener(
    settingsViewModel: SettingsViewModel,
    open: (TvDestination) -> Unit,
): (TvDestination) -> Unit {
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var settingsPending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settingsPending, settings) {
        if (settingsPending && settings is SettingsUiState.Ready) {
            settingsPending = false
            open(TvDestination.Settings)
        }
    }
    return { opened ->
        if (opened == TvDestination.Settings && settings !is SettingsUiState.Ready) settingsPending = true else open(opened)
    }
}

/** Home's pages over its rows: a see-all grid, the user's own requests, and a request's detail page above either. */
@Composable
private fun TvHubPages(
    pages: TvOpenPages,
    seeAllAccountId: Int?,
) {
    pages.seeAllRequests?.let { name ->
        TvRequestsGridOverlay(
            filter = RequestFilter.valueOf(name),
            detailOpen = pages.openRequestId != null,
            onOpenRequest = { pages.openRequestId = it },
            onDone = { pages.seeAllRequests = null },
        )
    }
    if (pages.seeAllAccount && seeAllAccountId != null) {
        TvAccountRequestsGridOverlay(
            accountId = seeAllAccountId,
            detailOpen = pages.openRequestId != null,
            onOpenRequest = { pages.openRequestId = it },
            onDone = { pages.seeAllAccount = false },
        )
    }
    TvDetailOverlays(openRequestId = pages.openRequestId, openIssueId = null, onCloseRequest = { pages.openRequestId = null })
}

/** The Issues tab's pages over its board: a see-all grid, and an issue's detail page above it. */
@Composable
private fun TvIssuesPages(pages: TvOpenPages) {
    pages.seeAllIssues?.let { name ->
        TvIssuesGridOverlay(
            filter = IssueFilter.valueOf(name),
            detailOpen = pages.openIssueId != null,
            onOpenIssue = { pages.openIssueId = it },
            onDone = { pages.seeAllIssues = null },
        )
    }
    TvDetailOverlays(openRequestId = null, openIssueId = pages.openIssueId, onCloseIssue = { pages.openIssueId = null })
}

/**
 * The request and issue pages above the page. Each page its own view models, cleared when it closes: a reopened page starts
 * fresh, and nothing piles up in the activity's store or outlives a change of server (#789).
 */
@Composable
private fun TvDetailOverlays(
    openRequestId: Int?,
    openIssueId: Int?,
    onCloseRequest: () -> Unit = {},
    onCloseIssue: () -> Unit = {},
) {
    openRequestId?.let { id ->
        ScopedViewModels("request-detail-$id") { TvRequestDetailOverlay(requestId = id, onDone = onCloseRequest) }
    }
    openIssueId?.let { id ->
        ScopedViewModels("issue-detail-$id") { TvIssueDetailOverlay(issueId = id, onDone = onCloseIssue) }
    }
}

/**
 * The tabs the bar can offer, in order, each with its own rule over what the server has said of this account. The first,
 * Home, is the start tab and is always offered; the rest follow the phone's rule for the matching hub section.
 */
private val TabRules: List<Pair<TvDestination, (HubOverview) -> Boolean>> =
    listOf(
        TvDestination.Hub to { _ -> true },
        TvDestination.Issues to { overview -> HubSection.Issues.isVisible(overview) },
    )

/** Each tab's own pages over its board, chosen by tab. */
private fun tabPages(
    tab: TvDestination,
    pages: TvOpenPages,
    seeAllAccountId: Int?,
): (@Composable BoxScope.() -> Unit)? =
    when (tab) {
        TvDestination.Hub -> {
            { TvHubPages(pages, seeAllAccountId) }
        }
        TvDestination.Issues -> {
            { TvIssuesPages(pages) }
        }
        TvDestination.Account, TvDestination.Settings -> null
    }

/** The tabs the server offers this account, by [TabRules]; the start tab alone when it has not said. */
internal fun offeredTabs(hub: HubUiState): List<TvDestination> {
    val overview = (hub as? HubUiState.Ready)?.overview
    return TabRules.filterIndexed { index, (_, rule) -> index == 0 || (overview != null && rule(overview)) }.map { it.first }
}
