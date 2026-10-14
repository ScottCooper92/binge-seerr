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
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.MaterialTheme
import com.binge.designsystem.tv.focus.TvFocusSink
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import com.binge.designsystem.tv.focus.tvExitFocusGroup
import com.binge.designsystem.tv.nav.LocalTvHostedAsOverlay
import com.binge.designsystem.tv.nav.LocalTvTabHosted
import com.binge.designsystem.tv.nav.LocalTvTopInset
import com.cooper.binge.companion.seerr.R

/** The pages of the connected TV app: the tabs in the top bar, and the two sheets it opens over them. */
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
 * The connected app under a bar that stays over every page, as the phone's bottom navigation does: the offered [tabs], the
 * first of them the start tab, and Account and Settings as a [sheet] over whichever tab shows, above the bar. Pages draw
 * under the bar and pad their content by its measured height, through `LocalTvTopInset`. An [overlay] (a detail page, a
 * see-all grid) belongs to the current tab and sits under the bar too. With one tab offered there is nothing to choose
 * between, so the bar shows no tabs, only the avatar and the gear, and the avatar stands where the current tab would.
 *
 * A [topLayer] is a title's own page, such as a request: a full screen drawn over everything, the bar included, with no top
 * inset. It traps focus and takes Back itself; when it closes, focus goes back to the card that opened it on the page beneath.
 *
 * Focus: selecting a tab leaves focus on it, and Down enters the page. Up out of a page's top edge lands on the current tab.
 * Back: a top layer, an overlay or a sheet takes it first; on a page it moves focus to the current tab; on the bar it leaves
 * any other tab for the start tab, and from the start tab leaves the app.
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
    /** The tabs offered, in order; the first is the start tab and is always among them. [tab] must be one of them. */
    tabs: List<TvDestination> = listOf(TvDestination.Hub, TvDestination.Issues),
    /** Whether the server has said which tabs this account gets; the bar stays hidden until it has. */
    tabsResolved: Boolean = true,
    account: TvTopBarAccount = TvTopBarAccount(name = "", avatarUrl = null),
    /** False while a full-screen task (editing the connection) is up: the bar hides and pages take no inset. */
    showBar: Boolean = true,
    topLayer: (@Composable () -> Unit)? = null,
    /** [content] draws a destination; its `close` returns to the start tab, so there it is a reselect and clears its pages. */
    content: @Composable (destination: TvDestination, close: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    var barHeightPx by remember { mutableIntStateOf(0) }
    var barFocused by remember { mutableStateOf(false) }
    val opener = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val entries = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val overlayEntry = remember { FocusRequester() }
    // Held here, not in the sheet: it hands focus back after the sheet has gone.
    val sheetCloser = rememberTvOverlayCloser(restoreTo = opener.getValue(sheet ?: tab), onClose = onCloseSheet)
    val start = tabs.first()
    // Where the bar is reached from a page: the current tab, or the avatar when the bar has no tabs to show.
    val barStop = opener.getValue(if (tabs.size > 1) tab else TvDestination.Account)
    val barUp = showBar && tabsResolved
    Box(modifier = modifier.fillMaxSize()) {
        TvShellBack(
            onStart = tab == start,
            barFocused = barFocused,
            barUp = barUp,
            covered = overlay != null || sheet != null || topLayer != null,
            barStop = barStop,
            onLeaveForStart = {
                onSelectTab(start)
                opener.getValue(start).requestFocus()
            },
        )
        CompositionLocalProvider(
            LocalTvTopInset provides with(density) { (if (showBar) barHeightPx else 0).toDp() },
            LocalTvHostedAsOverlay provides true,
        ) {
            TvShellPages(
                tab = tab,
                start = start,
                sheetOpen = sheet != null,
                topLayerOpen = topLayer != null,
                overlay = overlay,
                entries = entries,
                overlayEntry = overlayEntry,
                upToBar = if (barUp) barStop else null,
                content = { destination -> content(destination) { onSelectTab(start) } },
            )
        }
        TvTopBar(
            destinations = listOf(TvDestination.Account) + tabs.takeIf { it.size > 1 }.orEmpty() + TvDestination.Settings,
            current = tab,
            opener = opener,
            down = if (overlay != null) overlayEntry else entries.getValue(tab),
            visible = barUp,
            account = account,
            onFocusedChange = { barFocused = it },
            onSelect = { if (it.isSheet) onOpenSheet(it) else onSelectTab(it) },
            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { barHeightPx = it.height },
        )
        topLayer?.let { TvShellTopLayer(it) }
        if (sheet != null) {
            TvShellSheet(overlayOpen = overlay != null, onClose = sheetCloser::close) { content(sheet, sheetCloser::close) }
        }
    }
}

/**
 * Back when nothing above the pages takes it ([covered]) and the bar is up ([barUp]; a hidden bar cannot take focus, so Back
 * falls through to the system): on a page it goes to [barStop]; on the bar it leaves any other tab for the start tab, and
 * [onStart] it is not handled, so the app closes.
 */
@Composable
private fun TvShellBack(
    onStart: Boolean,
    barFocused: Boolean,
    barUp: Boolean,
    covered: Boolean,
    barStop: FocusRequester,
    onLeaveForStart: () -> Unit,
) {
    BackHandler(enabled = !covered && barUp && !(barFocused && onStart)) {
        if (barFocused) onLeaveForStart() else barStop.requestFocus()
    }
}

/**
 * The pages under the bar: the [start] tab, any other current tab above it, and the current tab's [overlay] above both. The
 * start tab claims first focus when the app opens, and stays composed under the others so it keeps its place. Each tab and
 * the overlay is entered through its own requester in [entries] and [overlayEntry], so Down from the bar lands in whichever
 * page is on top. [upToBar] is where Up from a page's top edge lands, or null while the bar takes no focus.
 */
@Composable
private fun TvShellPages(
    tab: TvDestination,
    start: TvDestination,
    sheetOpen: Boolean,
    topLayerOpen: Boolean,
    overlay: (@Composable BoxScope.() -> Unit)?,
    entries: Map<TvDestination, FocusRequester>,
    overlayEntry: FocusRequester,
    upToBar: FocusRequester?,
    content: @Composable (TvDestination) -> Unit,
) {
    // A sheet hands focus back itself, and a top layer over an overlay leaves it to the overlay, so the tab stays out of both.
    val tabTakesFocusBack = !sheetOpen && !(topLayerOpen && overlay != null)
    HandBackHost(
        entry = entries.getValue(start),
        active = tab == start && tabTakesFocusBack,
        modifier = Modifier.exitUpTo(upToBar),
    ) { content(start) }
    // A tab page that goes while it holds focus (its tab withdrawn) would leave focus on the bar's first item, so the start
    // tab takes it. Read as the tab changes, before the page detaches and reports losing focus.
    val tabPage = remember { TabPageFocus() }
    val handOffToStart = remember(tab) { tab == start && tabPage.held }
    if (tab != start) {
        CompositionLocalProvider(LocalTvTabHosted provides true) {
            key(tab) {
                HandBackHost(
                    entry = entries.getValue(tab),
                    active = tabTakesFocusBack,
                    modifier =
                        Modifier
                            .onFocusChanged { tabPage.held = it.hasFocus }
                            .background(MaterialTheme.colorScheme.background)
                            .then(if (overlay == null && !sheetOpen) Modifier.pageTrap(upToBar) else Modifier),
                ) { content(tab) }
            }
        }
    }
    LaunchedEffect(handOffToStart) { if (handOffToStart) restoreTvOverlayFocus(entries.getValue(start)) }
    // The overlay hands focus back as a tab page does, so a page closed over it returns to the overlay rather than the tab.
    overlay?.let {
        HandBackHost(entry = overlayEntry, active = !sheetOpen, modifier = Modifier.pageTrap(upToBar)) {
            Box(modifier = Modifier.fillMaxSize(), content = it)
        }
    }
}

/** Whether a tab page above the start tab holds focus, read without recomposing on every move. */
private class TabPageFocus {
    var held = false
}

/**
 * A title's own page over everything, the bar included: it fills the screen, so it takes no top inset. It draws its own
 * background, a page an opaque one and a side sheet its scrim, so a sheet can show the board through. Focus is trapped in
 * it; Back is the page's own, and closing it hands focus back to the page beneath.
 */
@Composable
private fun TvShellTopLayer(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTvTopInset provides Dp.Hairline, LocalTvHostedAsOverlay provides true) {
        HandBackHost(
            entry = remember { FocusRequester() },
            active = true,
            modifier = Modifier.tvExitFocusGroup(),
            content = content,
        )
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
