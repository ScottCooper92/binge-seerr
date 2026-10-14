package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.focus.TvFocusSink
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvExitFocusGroup
import com.binge.designsystem.tv.nav.LocalTvHostedAsOverlay
import com.binge.designsystem.tv.nav.LocalTvTabHosted
import com.binge.designsystem.tv.nav.LocalTvTopInset
import com.cooper.binge.companion.seerr.R

/** The pages of the connected TV app: the two tabs in the top bar, and the two sheets it opens over them. */
internal enum class TvDestination(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    /** Whether the page is a side sheet over the current tab, which draws its own scrim, rather than a tab. */
    val isSheet: Boolean = false,
) {
    Account("account", R.string.tv_rail_account, Icons.Filled.Person, isSheet = true),
    Hub("hub", R.string.tv_rail_hub, Icons.Filled.Home),
    Issues("issues", R.string.hub_section_issues, Icons.Filled.ReportProblem),
    Settings("settings", R.string.hub_section_settings, Icons.Filled.Settings, isSheet = true),
}

/**
 * The connected app under a bar that stays over every page, as the phone's bottom navigation does: Home and Issues are [tab]s,
 * and Account and Settings open as a [sheet] over whichever tab shows, above the bar. Pages draw under the bar and pad their
 * content by its measured height, through `LocalTvTopInset`. An [overlay] (a detail page, a see-all grid) belongs to the
 * current tab and sits under the bar too.
 *
 * Focus: selecting a tab leaves focus on it, and Down enters the page. Up out of a page's top edge lands on the current tab.
 * Back: an overlay or a sheet takes it first; on a page it moves focus to the current tab; on the bar it leaves Issues for
 * Home, and from Home leaves the app.
 */
@Composable
internal fun TvHomeShell(
    tab: TvDestination,
    sheet: TvDestination?,
    onSelectTab: (TvDestination) -> Unit,
    onOpenSheet: (TvDestination) -> Unit,
    onCloseSheet: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    /** Whether Issues is offered: only for an account the server lets list issues. */
    showIssues: Boolean = true,
    /** Whether the server has said which tabs this account gets; the bar stays hidden until it has. */
    tabsResolved: Boolean = true,
    account: TvTopBarAccount = TvTopBarAccount(name = "", avatarUrl = null),
    /** False while a full-screen task (editing the connection) is up: the bar hides and pages take no inset. */
    showBar: Boolean = true,
    /** [content] draws a destination; its `close` returns to Home, so on Home it is a reselect and clears Home's pages. */
    content: @Composable (destination: TvDestination, close: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    var barFocused by remember { mutableStateOf(false) }
    val opener = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val entries = remember { TvShellEntries() }
    // Held here, not in the sheet: it hands focus back after the sheet has gone.
    val sheetCloser = rememberTvOverlayCloser(restoreTo = opener.getValue(sheet ?: tab), onClose = onCloseSheet)
    val currentTab = opener.getValue(tab)
    val barUp = showBar && tabsResolved
    Box(modifier = modifier.fillMaxSize()) {
        TvShellBack(
            tab = tab,
            barFocused = barFocused,
            barUp = barUp,
            covered = overlay != null || sheet != null,
            opener = opener,
            onSelectTab = onSelectTab,
        )
        CompositionLocalProvider(
            LocalTvTopInset provides with(density) { (if (showBar) barHeightPx else 0).toDp() },
            LocalTvHostedAsOverlay provides true,
        ) {
            TvShellPages(
                tab = tab,
                sheetOpen = sheet != null,
                overlay = overlay,
                entries = entries,
                upToBar = if (barUp) currentTab else null,
                content = { destination -> content(destination) { onSelectTab(TvDestination.Hub) } },
            )
        }
        TvTopBar(
            destinations = TvDestination.entries.filter { it != TvDestination.Issues || showIssues },
            current = tab,
            opener = opener,
            down = entries.down(tab, overlayOpen = overlay != null),
            visible = barUp,
            account = account,
            onFocusedChange = { barFocused = it },
            onSelect = { if (it.isSheet) onOpenSheet(it) else onSelectTab(it) },
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { barHeightPx = it.height },
        )
        if (sheet != null) {
            TvShellSheet(overlayOpen = overlay != null, onClose = sheetCloser::close) { content(sheet, sheetCloser::close) }
        }
    }
}

/**
 * Back when nothing above the pages takes it ([covered]) and the bar is up ([barUp]; a hidden bar cannot take focus, so Back
 * falls through to the system): on a page it goes to the current tab; on the bar it leaves Issues
 * for Home, and from Home it is not handled, so the app closes.
 */
@Composable
private fun TvShellBack(
    tab: TvDestination,
    barFocused: Boolean,
    barUp: Boolean,
    covered: Boolean,
    opener: Map<TvDestination, FocusRequester>,
    onSelectTab: (TvDestination) -> Unit,
) {
    BackHandler(enabled = !covered && barUp && !(barFocused && tab == TvDestination.Hub)) {
        if (barFocused) onSelectTab(TvDestination.Hub)
        opener.getValue(if (barFocused) TvDestination.Hub else tab).requestFocus()
    }
}

/** Where focus enters each layer of the shell, so Down from the bar lands in whichever page is on top. */
private class TvShellEntries {
    val home = FocusRequester()
    val issues = FocusRequester()
    val overlay = FocusRequester()

    fun down(
        tab: TvDestination,
        overlayOpen: Boolean,
    ): FocusRequester =
        when {
            overlayOpen -> overlay
            tab == TvDestination.Issues -> issues
            else -> home
        }
}

/**
 * The pages under the bar: Home, Issues above it when chosen, and the current tab's [overlay] above both. Home claims first
 * focus when the app opens, and stays composed under Issues so it keeps its place. [upToBar] is the tab Up returns to from a
 * page's top edge, or null while the bar takes no focus.
 */
@Composable
private fun TvShellPages(
    tab: TvDestination,
    sheetOpen: Boolean,
    overlay: (@Composable BoxScope.() -> Unit)?,
    entries: TvShellEntries,
    upToBar: FocusRequester?,
    content: @Composable (TvDestination) -> Unit,
) {
    HandBackHost(
        entry = entries.home,
        active = tab == TvDestination.Hub && !sheetOpen,
        modifier = Modifier.exitUpTo(upToBar),
    ) { content(TvDestination.Hub) }
    if (tab == TvDestination.Issues) {
        CompositionLocalProvider(LocalTvTabHosted provides true) {
            HandBackHost(
                entry = entries.issues,
                active = !sheetOpen,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .then(if (overlay == null && !sheetOpen) Modifier.pageTrap(upToBar) else Modifier),
            ) { content(TvDestination.Issues) }
        }
    }
    overlay?.let {
        Box(modifier = Modifier.fillMaxSize().focusRequester(entries.overlay).pageTrap(upToBar), content = it)
    }
}

/** A sheet slides in over everything, the bar included; Back closes it unless a page above it takes Back first. */
@Composable
private fun TvShellSheet(
    overlayOpen: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = !overlayOpen, onBack = onClose)
    CompositionLocalProvider(LocalTvHostedAsOverlay provides true) {
        HandBackHost(entry = remember { FocusRequester() }, active = true, modifier = Modifier.tvExitFocusGroup(), content = content)
    }
}

/** Up out of a page's top edge lands on [tab]; Back is a request, not a move, so it gets out. */
private fun Modifier.exitUpTo(tab: FocusRequester?): Modifier =
    focusProperties {
        onExit = { if (requestedFocusDirection == FocusDirection.Up && tab != null) tab.requestFocus() }
    }

/** As [exitUpTo], and the other directions are refused, which would otherwise leak to the page beneath. */
private fun Modifier.pageTrap(tab: FocusRequester?): Modifier =
    focusProperties {
        onExit = {
            when (requestedFocusDirection) {
                FocusDirection.Up -> if (tab != null) tab.requestFocus() else cancelFocusChange()
                FocusDirection.Down, FocusDirection.Left, FocusDirection.Right -> cancelFocusChange()
                else -> Unit
            }
        }
    }.focusGroup()

/**
 * A surface whose focus is handed back to it, as the rail's content pane does, when a page or grid above it closes.
 *
 * Closing disposes the focused node, and Compose parks focus on the first focusable thing left, which is the wrong
 * card, and a request into a group that already holds focus skips its entry routing. So the surface carries a [TvFocusSink]
 * while nothing in it has focus; the sink takes the parked focus, and the hand-off then enters the content's own group,
 * where the hub's memory lands the card or tile that opened the page (#1509).
 */
@Composable
private fun HandBackHost(
    entry: FocusRequester,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hostHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().onFocusChanged { hostHasFocus = it.hasFocus }.focusGroup()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .focusRequester(entry)
                    .focusGroup(),
        ) { content() }
        if (active && !contentHasFocus) TvFocusSink()
    }
    val sinkHoldsFocus = active && hostHasFocus && !contentHasFocus
    LaunchedEffect(sinkHoldsFocus) {
        if (sinkHoldsFocus) restoreTvOverlayFocus(entry)
    }
}
