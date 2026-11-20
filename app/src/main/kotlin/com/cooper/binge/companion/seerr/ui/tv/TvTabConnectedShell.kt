package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.binge.designsystem.tv.focus.TvShellArrivalNone
import com.cooper.binge.companion.seerr.demo.isDemoOrigin
import com.cooper.binge.companion.seerr.ui.ScopedViewModels
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssuesViewModel
import com.cooper.binge.companion.seerr.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Trial of the design system's tab scaffold (binge-design-system#1392) in the connected TV app: Requests and Issues are real
 * tabs, Search, Movies and TV are empty pages, and the gear opens the Settings sheet as today. Not for merge.
 */
@Composable
internal fun TvTabConnectedShell(
    hubViewModel: HubViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    issuesViewModel: IssuesViewModel = hiltViewModel(),
    linkedRequests: Flow<Int> = emptyFlow(),
) {
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val overview = (hub as? HubUiState.Ready)?.overview
    val pages = rememberTvOpenPages()
    val tabs =
        buildList {
            addAll(listOf(TvTab.Search, TvTab.Movies, TvTab.Tv))
            add(TvTab.Requests)
            if (overview == null || HubSection.Issues.isVisible(overview)) add(TvTab.Issues)
        }
    var chosen by rememberSaveable { mutableStateOf(TvTab.Requests) }
    val tab = chosen.takeIf { it in tabs } ?: TvTab.Requests
    var sheet by rememberSaveable { mutableStateOf(false) }
    val openSheet = rememberSheetOpener(settingsViewModel) { sheet = true }
    LaunchedEffect(linkedRequests) {
        linkedRequests.collect { id ->
            sheet = false
            chosen = TvTab.Requests
            pages[TvDestination.Hub].detailId = id
        }
    }
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    val destination = tab.destination()
    val content = remember { FocusRequester() }
    TvTabShell(
        tab = tab,
        tabs = tabs,
        home = TvTab.Requests,
        onSelectTab = { selected -> if (selected == tab) destination?.let(pages::clear) else chosen = selected },
        sheetOpen = sheet,
        onOpenSheet = { openSheet(TvDestination.Settings) },
        onCloseSheet = { sheet = false },
        account =
            rememberTvAccount(
                hubViewModel = hubViewModel,
                hub = hub,
                demo = (hub as? HubUiState.Ready)?.server?.baseUrl?.isDemoOrigin() == true,
                onSignOut = settingsViewModel::signOut.takeIf { settings.signsInWithSession() },
            ),
        contentRequester = content,
        topLayer = destination?.let { detailPage(it, pages[it], onIssueChanged = issuesViewModel::refreshCounts) },
        onCloseTopLayer = { destination?.let { pages[it].detailId = null } },
        sheet = { closer ->
            TvSettingsEntry(onDismissRequest = closer::close, closer = closer, viewModel = settingsViewModel)
        },
    ) { current ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (current) {
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
                else -> TvEmptyTab(current)
            }
            destination?.let { tabPages(it, pages, seeAllAccountId = overview?.account?.id)?.invoke(this) }
        }
    }
    if (editingConnection) ScopedViewModels("edit-connection") { TvEditConnectionOverlay(onDone = { editingConnection = false }) }
}

private fun TvTab.destination(): TvDestination? =
    when (this) {
        TvTab.Requests -> TvDestination.Hub
        TvTab.Issues -> TvDestination.Issues
        else -> null
    }

/** A discovery tab that has no page yet. */
@Composable
private fun TvEmptyTab(tab: TvTab) {
    TvShellArrivalNone()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(tab.labelRes))
    }
}
