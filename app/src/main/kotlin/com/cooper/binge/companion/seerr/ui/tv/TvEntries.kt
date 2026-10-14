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
import com.cooper.binge.companion.seerr.ui.issues.IssuesViewModel
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SettingsViewModel
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.tv.requests.KEY_MY_REQUESTS

/**
 * The connected television: Home and Issues as tabs under the top bar, with Account and Settings opening as sheets over them,
 * each bound to the same ViewModel as its phone screen, and the edit-connection form as a full-screen overlay above them.
 */
@Composable
internal fun TvConnectedShell(
    hubViewModel: HubViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    // The Issues tab's own, so an issue changed in its sheet moves the tab's counts.
    issuesViewModel: IssuesViewModel = hiltViewModel(),
) {
    // The hub state the Account page reads, for the signed-in user's id and which tabs are offered.
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val account = (hub as? HubUiState.Ready)?.overview?.account
    val pages = rememberTvOpenPages()
    val tabsResolved = hub !is HubUiState.Loading
    // Until the hub answers, the bar is hidden and every tab stands, so a tab kept across a rotation is not dropped early.
    val tabs = if (tabsResolved) offeredTabs((hub as? HubUiState.Ready)?.overview) else TabRules.map { it.first }
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
                pages[tab].seeAll == null -> null
                else -> tabPages(tab, pages, seeAllAccountId = account?.id)
            },
        topLayer = detailPage(tab, pages[tab], onIssueChanged = issuesViewModel::refreshCounts).takeUnless { editingConnection },
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
                    onOpenRequest = { pages[TvDestination.Hub].detailId = it },
                    onSeeAll = { pages[TvDestination.Hub].seeAll = it.name },
                    onSeeAllMine = { pages[TvDestination.Hub].seeAll = SEE_ALL_MINE },
                )
            TvDestination.Issues ->
                TvIssuesEntry(
                    onOpenIssue = { pages[TvDestination.Issues].detailId = it },
                    onSeeAll = { pages[TvDestination.Issues].seeAll = it.name },
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

/** Home's see-all key for the signed-in user's own requests, which belong to no filter. */
private const val SEE_ALL_MINE = KEY_MY_REQUESTS

/** Home's see-all page over its rows: a filter's grid, or the user's own requests. A request's page opens above it, over the bar. */
@Composable
private fun TvHubPages(
    pages: TvTabPages,
    seeAllAccountId: Int?,
) {
    when (val seeAll = pages.seeAll) {
        null -> Unit
        SEE_ALL_MINE ->
            if (seeAllAccountId != null) {
                TvAccountRequestsGridOverlay(
                    accountId = seeAllAccountId,
                    detailOpen = pages.detailId != null,
                    onOpenRequest = { pages.detailId = it },
                    onDone = { pages.seeAll = null },
                )
            }
        else ->
            TvRequestsGridOverlay(
                filter = RequestFilter.valueOf(seeAll),
                detailOpen = pages.detailId != null,
                onOpenRequest = { pages.detailId = it },
                onDone = { pages.seeAll = null },
            )
    }
}

/** The Issues tab's see-all page over its board. An issue's page opens above it, over the bar. */
@Composable
private fun TvIssuesPages(pages: TvTabPages) {
    pages.seeAll?.let { name ->
        TvIssuesGridOverlay(
            filter = IssueFilter.valueOf(name),
            detailOpen = pages.detailId != null,
            onOpenIssue = { pages.detailId = it },
            onDone = { pages.seeAll = null },
        )
    }
}

/**
 * The tab's open title over the bar: a request's page on Home, an issue's sheet on Issues. Each page has its own view
 * models, cleared when it closes: a reopened page starts fresh, and nothing piles up in the activity's store or outlives a
 * change of server (#789).
 */
private fun detailPage(
    tab: TvDestination,
    pages: TvTabPages,
    onIssueChanged: () -> Unit,
): (@Composable () -> Unit)? {
    val id = pages.detailId ?: return null
    val close = { pages.detailId = null }
    return when (tab) {
        TvDestination.Hub -> {
            { ScopedViewModels("request-detail-$id") { TvRequestDetailOverlay(requestId = id, onDone = close) } }
        }
        TvDestination.Issues -> {
            { ScopedViewModels("issue-detail-$id") { TvIssueSheetEntry(issueId = id, onDone = close, onIssueChanged = onIssueChanged) } }
        }
        TvDestination.Account, TvDestination.Settings -> null
    }
}

/**
 * The tabs the bar can offer, in order, each with its own rule over what the server has said of this account. The first,
 * Home, is the start tab, and its rule offers it always; the rest follow the phone's rule for the matching hub section.
 */
private val TabRules: List<Pair<TvDestination, (HubOverview) -> Boolean>> =
    listOf(
        TvDestination.Hub to { _ -> true },
        TvDestination.Issues to { overview -> HubSection.Issues.isVisible(overview) },
    )

/** Each tab's see-all page over its board, chosen by tab. */
private fun tabPages(
    tab: TvDestination,
    pages: TvOpenPages,
    seeAllAccountId: Int?,
): (@Composable BoxScope.() -> Unit)? =
    when (tab) {
        TvDestination.Hub -> {
            { TvHubPages(pages[tab], seeAllAccountId) }
        }
        TvDestination.Issues -> {
            { TvIssuesPages(pages[tab]) }
        }
        TvDestination.Account, TvDestination.Settings -> null
    }

/** The tabs [TabRules] offers for what the server has said of this account; the start tab alone while it has said nothing. */
internal fun offeredTabs(overview: HubOverview?): List<TvDestination> =
    if (overview == null) listOf(TabRules.first().first) else TabRules.filter { (_, rule) -> rule(overview) }.map { it.first }
