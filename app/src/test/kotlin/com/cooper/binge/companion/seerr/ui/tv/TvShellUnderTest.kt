package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.binge.designsystem.tv.focus.TvOverlayCloser

/**
 * The real [TvTabShell] with the old home shell's call shape, so the hand-off, top-layer, sheet and clipping tests that were written against
 * it still drive the shell they describe. A [TvDestination] stands for its tab, and the sheet is Settings. An [overlay] is the shell's own, as the connected shell passes a see-all grid. The bar is always drawn.
 */
@Composable
internal fun TvShellUnderTest(
    tab: TvDestination,
    sheet: TvDestination?,
    onSelectTab: (TvDestination) -> Unit,
    onOpenSheet: (TvDestination) -> Unit,
    onCloseSheet: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    tabs: List<TvDestination> = listOf(TvDestination.Hub, TvDestination.Issues),
    tabsResolved: Boolean = true,
    account: TvTopBarAccount = TvTopBarAccount(name = "", avatarUrl = null),
    topLayer: (@Composable (TvOverlayCloser) -> Unit)? = null,
    onCloseTopLayer: () -> Unit = {},
    content: @Composable (destination: TvDestination, close: () -> Unit, closer: TvOverlayCloser?) -> Unit,
) {
    val tabOf = { destination: TvDestination -> if (destination == TvDestination.Issues) TvTab.Issues else TvTab.Requests }
    val offered = tabs.map(tabOf)
    TvTabShell(
        tab = tabOf(tab),
        tabs = offered,
        home = offered.first(),
        pendingTabs = if (tabsResolved) emptySet() else ServerGatedTabs,
        overlayOpen = overlay != null,
        onSelectTab = { selected -> onSelectTab(selected.destination ?: TvDestination.Hub) },
        sheetOpen = sheet != null,
        onOpenSheet = { onOpenSheet(TvDestination.Settings) },
        onCloseSheet = onCloseSheet,
        account = account,
        contentRequester = remember { FocusRequester() },
        modifier = modifier,
        topLayer = topLayer,
        onCloseTopLayer = onCloseTopLayer,
        sheet = { closer -> content(TvDestination.Settings, closer::close, closer) },
    ) { current ->
        TvPageWithOverlay(overlay) { content(current.destination ?: TvDestination.Hub, {}, null) }
    }
}
