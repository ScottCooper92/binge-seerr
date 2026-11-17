package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeNavPresentation
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.ui.hub.HubScreen
import com.cooper.binge.companion.seerr.ui.hub.previewActions
import com.cooper.binge.companion.seerr.ui.hub.previewReady

/**
 * The discovery shell on a phone: the bottom bar over the Settings tab, which is the hub as it is today, and over a tab whose
 * screen has not landed. The Spanish frame carries the longest labels ("Películas"), and the large-text frames the bar at 1.5x
 * and 2x.
 */
class ShellScreenshotTest {
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun settingsTab() = ShellFrame(SeerrTab.Settings)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun placeholderTab() = ShellFrame(SeerrTab.Movies)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = ShellFrame(SeerrTab.Settings)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun largeText() = ShellFrame(SeerrTab.Settings)
}

@Composable
private fun ShellFrame(selected: SeerrTab) {
    val state =
        TabShellState(
            tabs = SeerrTab.entries,
            selectedIndex = mutableIntStateOf(selected.ordinal),
            stacks = SeerrTab.entries.associateWith { NavBackStack<NavKey>(MoviesRoot) },
        )
    TabShell(state = state, items = seerrTabItems(), presentation = BingeNavPresentation.BottomBar) { tab ->
        when (tab) {
            SeerrTab.Settings -> HubScreen(state = previewReady(), actions = previewActions())
            else -> TabPlaceholder(tab)
        }
    }
}
