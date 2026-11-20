package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.companion.sdk.attempt
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.nav.TvAccountMenuRow
import com.binge.designsystem.tv.nav.TvNavItem
import com.binge.designsystem.tv.nav.TvTopBarScaffold
import com.binge.designsystem.tv.nav.TvTopBarSlots
import com.cooper.binge.companion.seerr.R

/** The pages of the TV shell's bar, in the order it shows them. Search, Movies and TV are the discovery tabs. */
internal enum class TvTab(
    val labelRes: Int,
    val icon: ImageVector,
    /** The page this tab opens things over, which keeps its see-all and detail pages; null for a tab with no board of its own yet. */
    val destination: TvDestination? = null,
    val isDiscovery: Boolean = false,
    /** Whether the page's backdrop runs behind the bar, as the immersive boards' does; the page pads its focusable content by the bar's height. */
    val behindBar: Boolean = false,
) {
    Search(R.string.shell_tab_search, Icons.Filled.Search, isDiscovery = true),
    Movies(R.string.shell_tab_movies, Icons.Filled.Movie, isDiscovery = true),
    Tv(R.string.shell_tab_tv, Icons.Filled.Tv, isDiscovery = true),
    Requests(R.string.hub_section_requests, Icons.Filled.Inbox, TvDestination.Hub, behindBar = true),
    Issues(R.string.hub_section_issues, Icons.Filled.ReportProblem, TvDestination.Issues, behindBar = true),
}

/**
 * Who is signed in, for the avatar at the bar's start and the menu that opens from it. [detail] is the line under the name; [extraContent]
 * sits under the header (the quota, say) and is composed only while the menu is open; [rows] are the menu's actions. [demo] says so
 * beside the name. [initiallyOpen] draws the menu open until focus first reaches it, for a screenshot.
 */
internal class TvTopBarAccount(
    val name: String,
    val avatarUrl: String?,
    val demo: Boolean = false,
    val detail: String? = null,
    val rows: List<TvAccountMenuRow> = emptyList(),
    val extraContent: (@Composable ColumnScope.() -> Unit)? = null,
    val initiallyOpen: Boolean = false,
)

private const val ACCOUNT_KEY = "account"
private const val SETTINGS_KEY = "settings"

/**
 * The connected TV app on the design system's tab scaffold: the account avatar, [tabs] and the Settings gear across the bar, and the
 * current page below it. [home] is the start tab; [pendingTabs] are hidden until the server says this account gets them. A [sheet]
 * (Settings) and a [topLayer] (a title's page) draw over the bar. [overlayOpen] says a see-all page is up ([TvPageWithOverlay] draws it).
 * While any is up the scaffold's Back and focus rules rest, and when one closes the page takes focus back. Every page claims the shell's
 * arrival with `tvShellArrivalTarget`, or `TvShellArrivalNone` when it has nothing to focus.
 */
@Composable
internal fun TvTabShell(
    tab: TvTab,
    tabs: List<TvTab>,
    home: TvTab,
    onSelectTab: (TvTab) -> Unit,
    sheetOpen: Boolean,
    onOpenSheet: () -> Unit,
    onCloseSheet: () -> Unit,
    account: TvTopBarAccount,
    contentRequester: FocusRequester,
    modifier: Modifier = Modifier,
    pendingTabs: Set<TvTab> = emptySet(),
    overlayOpen: Boolean = false,
    topLayer: (@Composable (TvOverlayCloser) -> Unit)? = null,
    onCloseTopLayer: () -> Unit = {},
    sheet: @Composable (TvOverlayCloser) -> Unit = {},
    content: @Composable (TvTab) -> Unit,
) {
    val accountLabel = stringResource(R.string.tv_account)
    val settingsLabel = stringResource(R.string.shell_tab_settings)
    val demoTag = if (account.demo) stringResource(R.string.demo_indicator) else null
    val labels = tabs.associateWith { stringResource(it.labelRes) }
    val slots =
        remember(tabs, labels, account.name, account.avatarUrl, accountLabel, settingsLabel) {
            TvTopBarSlots(
                header =
                    TvNavItem(
                        key = ACCOUNT_KEY,
                        label = accountLabel,
                        icon = Icons.Filled.Person,
                        displayName = account.name.takeIf { it.isNotBlank() },
                        avatarUrl = account.avatarUrl,
                    ),
                items = tabs.map { TvNavItem(key = it, label = labels.getValue(it), icon = it.icon) },
                footer = TvNavItem(key = SETTINGS_KEY, label = settingsLabel, icon = Icons.Filled.Settings),
            )
        }
    // The scaffold puts focus on the gear once for each time this moves, which it does as the Settings sheet it opened has gone.
    var gearReturn by remember { mutableIntStateOf(0) }
    val sheetCloser =
        rememberTvOverlayCloser(
            restoreTo = null,
            onClose = {
                gearReturn++
                onCloseSheet()
            },
        )
    val topLayerCloser = rememberTvOverlayCloser(restoreTo = null, onClose = onCloseTopLayer)
    PageTakesFocusBack(topLayerUp = topLayer != null, overlayUp = overlayOpen, contentRequester)
    Box(modifier = modifier.fillMaxSize()) {
        TvTopBarScaffold(
            slots = slots,
            selectedKey = tab,
            homeKey = home,
            onSelect = { key ->
                when (key) {
                    SETTINGS_KEY -> onOpenSheet()
                    is TvTab -> onSelectTab(key)
                    else -> Unit
                }
            },
            contentDepth = 0,
            onGoHome = { onSelectTab(home) },
            overlayOpen = sheetOpen || topLayer != null || overlayOpen,
            contentFocusRequester = contentRequester,
            accountRows = account.rows,
            accountDetail = account.detail,
            accountTag = demoTag,
            accountExtraContent = account.extraContent,
            accountInitiallyOpen = account.initiallyOpen,
            pendingKeys = pendingTabs,
            gearReturnEpoch = gearReturn,
            contentBehindBar = tab.behindBar,
        ) {
            // Closing a layer disposes the focused node and focus parks inside the page, where a request into the group would skip its routing:
            // the host's sink takes the parked focus, so the request that follows enters the page and its own memory lands the card.
            HandBackHost(entry = remember { FocusRequester() }, active = !sheetOpen) { content(tab) }
        }
        topLayer?.let { TvShellTopLayer(topLayerCloser, it) }
        if (sheetOpen) TvShellSheet(overlayOpen = topLayer != null, closer = sheetCloser) { sheet(sheetCloser) }
    }
}

/**
 * A page with a see-all [overlay] over it: while the overlay is up the page takes no focus, so the remote cannot reach a card the overlay
 * covers, and the overlay is its own focus group.
 */
@Composable
internal fun TvPageWithOverlay(
    overlay: (@Composable BoxScope.() -> Unit)?,
    page: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier.fillMaxSize().then(
                    if (overlay ==
                        null
                    ) {
                        Modifier
                    } else {
                        Modifier.focusProperties { canFocus = false }
                    },
                ),
        ) { page() }
        overlay?.let { Box(modifier = Modifier.fillMaxSize().focusGroup(), content = it) }
    }
}

/**
 * Puts focus back on the page, or on the overlay over it, once a layer has closed: a title's page ([topLayerUp]) or a see-all page ([overlayUp]).
 * The bar is composed before the page, so focus a closing layer parks lands on the bar, and the page, entered through [contentRequester],
 * lands the card that opened the layer by its own memory. Asked after the layer has left the composition, so the request is not made into a
 * layer still there.
 */
@Composable
private fun PageTakesFocusBack(
    topLayerUp: Boolean,
    overlayUp: Boolean,
    contentRequester: FocusRequester,
) {
    var topWasUp by remember { mutableStateOf(topLayerUp) }
    var overlayWasUp by remember { mutableStateOf(overlayUp) }
    LaunchedEffect(topLayerUp, overlayUp) {
        val closed = (topWasUp && !topLayerUp) || (overlayWasUp && !overlayUp)
        if (closed) attempt { contentRequester.requestFocus() }
        topWasUp = topLayerUp
        overlayWasUp = overlayUp
    }
}
