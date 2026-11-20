package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrListPanePreview
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.AccountBarState
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.ui.hub.HubScreen
import com.cooper.binge.companion.seerr.ui.hub.previewActions
import com.cooper.binge.companion.seerr.ui.hub.previewReady
import com.cooper.homelab.core.uiandroid.shell.TabShellState

/**
 * The tab bar on a phone, and on a foldable and a tablet where the bar is the same pill or the design system's rail: the floating pill over the Settings tab, which is the hub as it is today and clears the pill at its
 * last row, and over a tab whose screen has not landed. The Spanish frame carries the longest labels ("Películas"), and the
 * large-text frames the pill at 1.3x and 2x. A screen opened from a tab is above the shell and never sees the pill.
 */
class ShellScreenshotTest {
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun settingsTab() = ShellFrame(SeerrTab.Settings)

    /** The Settings item is the account's avatar, ringed as selected: initials where the server has no photo. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun accountSelected() = ShellFrame(SeerrTab.Settings, ACCOUNT)

    /** The avatar while another tab is selected. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun accountNotSelected() = ShellFrame(SeerrTab.Search, ACCOUNT)

    /** The avatar at font scale 1.3 and 2, with the longest label under it. */
    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun accountLargeText13() = ShellFrame(SeerrTab.Settings, ACCOUNT)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun accountLargeText() = ShellFrame(SeerrTab.Settings, ACCOUNT)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun placeholderTab() = ShellFrame(SeerrTab.Search)

    @PreviewTest
    @SeerrListPanePreview
    @Composable
    fun settingsTabFoldable() = ShellFrame(SeerrTab.Settings)

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun settingsTabTablet() = ShellFrame(SeerrTab.Settings)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = ShellFrame(SeerrTab.Settings)

    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun largeText13() = ShellFrame(SeerrTab.Settings)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = ShellFrame(SeerrTab.Settings)
}

private val ACCOUNT =
    AccountBarState.Ready(AccountBar(HubAccount(id = 1, name = "Ada Lovelace", isAdmin = true, avatarUrl = null), quota = null))

@Composable
private fun ShellFrame(
    selected: SeerrTab,
    account: AccountBarState = AccountBarState.Loading,
) {
    val state =
        TabShellState(
            tabs = SeerrTab.entries,
            selectedIndex = mutableIntStateOf(selected.ordinal),
            stacks = SeerrTab.entries.associateWith { NavBackStack<NavKey>(MoviesRoot) },
        )
    SeerrTabShell(
        state,
        account = account,
        discover = { TabPlaceholder(SeerrTab.Movies) },
        settings = { HubScreen(state = previewReady(), actions = previewActions()) },
    )
}
