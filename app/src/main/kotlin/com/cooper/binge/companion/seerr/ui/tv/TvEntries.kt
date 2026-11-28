package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.binge.designsystem.tv.focus.TvShellArrivalNone
import com.cooper.binge.companion.seerr.demo.isDemoOrigin
import com.cooper.binge.companion.seerr.flags.TvDiscoveryEnabled
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
import com.cooper.homelab.core.uiandroid.flags.LocalFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The connected television: the account avatar, the tabs and the Settings gear across the design system's top bar, each tab bound to the
 * same ViewModel as its phone screen. Requests and Issues are always the app's own; Search, Movies and TV are the discovery tabs, offered
 * only with the TV discovery flag on, and then the app opens on Movies. Settings opens as a sheet over the tab, the account as the
 * avatar's menu, and the edit-connection form as a full-screen layer above everything.
 */
@Composable
internal fun TvConnectedShell(
    hubViewModel: HubViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    // The Issues tab's own, so an issue changed in its sheet moves the tab's counts.
    issuesViewModel: IssuesViewModel = hiltViewModel(),
    // A request's link, from a notification or a host's hand-off: its page opens over Requests.
    linkedRequests: Flow<Int> = emptyFlow(),
) {
    // The hub state the account menu reads, for the signed-in user's id and which tabs are offered.
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val overview = (hub as? HubUiState.Ready)?.overview
    val account = overview?.account
    val pages = rememberTvOpenPages()
    val resolved = hub !is HubUiState.Loading
    val discovery = LocalFlags.current[TvDiscoveryEnabled]
    val home = if (discovery) TvTab.Movies else TvTab.Requests
    val tabs = offeredTabs(overview, resolved = resolved, discovery = discovery)
    // Until the server has said which tabs this account gets, the ones it decides stand, hidden, so a tab kept across a rotation is not dropped early.
    val pending = if (resolved) emptySet() else ServerGatedTabs
    var chosenTab by rememberSaveable { mutableStateOf(home) }
    // A tab withdrawn under the user (a permission change, a reconnect as someone else, the flag turning off) hands over to the start
    // tab at once; the effect then drops its pages and settles the choice.
    val tab = chosenTab.takeIf { it in tabs } ?: home
    LaunchedEffect(tabs) {
        pages.reconcile(TvDestination.Hub, tabs.mapNotNull { it.destination })
        if (chosenTab !in tabs) chosenTab = home
    }
    var sheet by rememberSaveable { mutableStateOf(false) }
    val openSheet = rememberSheetOpener(settingsViewModel) { sheet = true }
    LaunchedEffect(linkedRequests) {
        linkedRequests.collect { id ->
            sheet = false
            chosenTab = TvTab.Requests
            pages[TvDestination.Hub].detailId = id
        }
    }
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val analytics = LocalAnalytics.current
    LaunchedEffect(
        tab,
        sheet,
    ) { analytics.screen((if (sheet) TvDestination.Settings else tab.destination ?: TvDestination.Hub).screenName()) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    val destination = tab.destination
    val content = remember { FocusRequester() }
    TvTabShell(
        tab = tab,
        tabs = tabs,
        home = home,
        pendingTabs = pending,
        overlayOpen = destination?.let { pages[it].drawsSeeAll(account?.id) } == true,
        onSelectTab = { selected -> if (selected == tab) destination?.let(pages::clear) else chosenTab = selected },
        sheetOpen = sheet,
        onOpenSheet = { openSheet(TvDestination.Settings) },
        onCloseSheet = { sheet = false },
        account =
            rememberTvAccount(
                hubViewModel = hubViewModel,
                hub = hub,
                demo = (hub as? HubUiState.Ready)?.server?.baseUrl?.isDemoOrigin() == true,
                // Only a session sign-in has a session to end; a server's API key has nothing to sign out of.
                onSignOut = settingsViewModel::signOut.takeIf { settings.signsInWithSession() },
                onOpenAccount = {
                    chosenTab = TvTab.Requests
                    pages[TvDestination.Hub].seeAll = SEE_ALL_MINE
                },
            ),
        contentRequester = content,
        topLayer =
            if (editingConnection) {
                { closer -> ScopedViewModels("edit-connection") { TvEditConnectionOverlay(onDone = closer::close) } }
            } else {
                destination?.let { detailPage(it, pages[it], onIssueChanged = issuesViewModel::refreshCounts) }
            },
        onCloseTopLayer = { if (editingConnection) editingConnection = false else destination?.let { pages[it].detailId = null } },
        sheet = { closer -> TvSettingsEntry(onDismissRequest = closer::close, closer = closer, viewModel = settingsViewModel) },
    ) { current ->
        val overlay = destination?.takeIf { pages[it].drawsSeeAll(account?.id) }?.let { tabPages(it, pages, seeAllAccountId = account?.id) }
        TvPageWithOverlay(overlay) {
            when (current) {
                // Requests is what needs attention: the rows over a backdrop, once the server answers.
                TvTab.Requests ->
                    TvHomeEntry(
                        onReconnect = { editingConnection = true },
                        onOpenRequest = { pages[TvDestination.Hub].detailId = it },
                        onSeeAll = { pages[TvDestination.Hub].seeAll = it.name },
                        onSeeAllMine = { pages[TvDestination.Hub].seeAll = SEE_ALL_MINE },
                    )
                TvTab.Issues ->
                    TvIssuesEntry(
                        onOpenIssue = { pages[TvDestination.Issues].detailId = it },
                        onSeeAll = { pages[TvDestination.Issues].seeAll = it.name },
                    )
                // The discovery screens are not here yet, so a tab that is switched on for a check shows no board.
                TvTab.Search, TvTab.Movies, TvTab.Tv -> TvEmptyTab()
            }
        }
    }
}

/** A discovery tab that has no screen yet: nothing to focus, so it settles the shell's arrival. */
@Composable
private fun TvEmptyTab() {
    TvShellArrivalNone()
    Box(modifier = Modifier.fillMaxSize())
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

/**
 * Whether the tab's see-all page has anything to draw. The user's own requests need the account, which a failed hub load leaves unknown:
 * restored over that, nothing would be drawn, so the page under it would still be told an overlay covers it and nothing could take focus.
 */
internal fun TvTabPages.drawsSeeAll(accountId: Int?): Boolean = seeAll != null && (seeAll != SEE_ALL_MINE || accountId != null)

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
): (@Composable (TvOverlayCloser) -> Unit)? {
    val id = pages.detailId ?: return null
    return when (tab) {
        TvDestination.Hub -> {
            { closer -> ScopedViewModels("request-detail-$id") { TvRequestDetailOverlay(requestId = id, onDone = closer::close) } }
        }
        TvDestination.Issues -> {
            { closer ->
                ScopedViewModels("issue-detail-$id") {
                    TvIssueSheetEntry(issueId = id, onDone = closer::close, onIssueChanged = onIssueChanged, closer = closer)
                }
            }
        }
        TvDestination.Settings -> null
    }
}

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
        TvDestination.Settings -> null
    }

/** The tabs the server decides: they stay hidden until it has said what this account gets. */
internal val ServerGatedTabs: Set<TvTab> = setOf(TvTab.Requests, TvTab.Issues)

/**
 * The tabs the bar can offer, in order, each with its own rule over what the server has said of this account. The discovery tabs follow the
 * TV discovery flag alone; Requests and Issues follow the phone's rule for the matching hub section. While the server has not answered
 * ([resolved] false) they all stand, and once it has answered without an account only Requests does.
 */
internal fun offeredTabs(
    overview: HubOverview?,
    resolved: Boolean,
    discovery: Boolean,
): List<TvTab> =
    TvTab.entries.filter { tab ->
        when {
            tab.isDiscovery -> discovery
            !resolved -> true
            overview == null -> tab == TvTab.Requests
            tab == TvTab.Requests -> HubSection.Requests.isVisible(overview)
            else -> HubSection.Issues.isVisible(overview)
        }
    }
