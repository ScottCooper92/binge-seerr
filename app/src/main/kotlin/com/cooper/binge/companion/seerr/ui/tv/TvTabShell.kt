package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.nav.TvNavItem
import com.binge.designsystem.tv.nav.TvTopBarScaffold
import com.binge.designsystem.tv.nav.TvTopBarSlots
import com.cooper.binge.companion.seerr.R

/** The pages of the TV shell's bar, in the order it shows them. Search, Movies and TV are the discovery tabs. */
internal enum class TvTab(
    val labelRes: Int,
    val icon: ImageVector,
) {
    Search(R.string.shell_tab_search, Icons.Filled.Search),
    Movies(R.string.shell_tab_movies, Icons.Filled.Movie),
    Tv(R.string.shell_tab_tv, Icons.Filled.Tv),
    Requests(R.string.hub_section_requests, Icons.Filled.Inbox),
    Issues(R.string.hub_section_issues, Icons.Filled.ReportProblem),
}

private const val ACCOUNT_KEY = "account"
private const val SETTINGS_KEY = "settings"

/**
 * The connected TV app on the design system's tab scaffold: [tabs] across the bar, the account avatar and the Settings gear, and the
 * current page below it. [home] is the start tab. A [sheet] (Settings) and a [topLayer] (a title's page) are drawn over the bar, and
 * the scaffold's own Back and focus rules rest while either is up.
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
    topLayer: (@Composable (TvOverlayCloser) -> Unit)? = null,
    onCloseTopLayer: () -> Unit = {},
    sheet: @Composable (TvOverlayCloser) -> Unit = {},
    content: @Composable (TvTab) -> Unit,
) {
    val accountLabel = stringResource(R.string.tv_account)
    val settingsLabel = stringResource(R.string.shell_tab_settings)
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
    // The bar is composed before the page here, so focus parked by the layer going lands on the bar: the closer sends it back to the page.
    val topLayerCloser = rememberTvOverlayCloser(restoreTo = contentRequester, onClose = onCloseTopLayer)
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
            overlayOpen = sheetOpen || topLayer != null,
            contentFocusRequester = contentRequester,
            accountRows = account.rows,
            gearReturnEpoch = gearReturn,
        ) { content(tab) }
        topLayer?.let { TvShellTopLayer(topLayerCloser, it) }
        if (sheetOpen) TvShellSheet(overlayOpen = topLayer != null, closer = sheetCloser) { sheet(sheetCloser) }
    }
}
