package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview

/** The "see all" grid: its order and the filter the row started with, over posters, and its empty, loading and failed states. */
class DiscoverGridScreenshotTest {
    /** The layout, once: the order chip and the row's own filter, over a grid with every status. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun filtered() = Frame(readyGridState())

    /** The filter cleared: only the order chip is left. */
    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun filteredTablet() = Frame(readyGridState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unfiltered() = Frame(unfilteredGridState())

    /** A list that takes no order and no filter, such as trending: no bar at all. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun plain() = Frame(plainGridState())

    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun font13() = Frame(readyGridState())

    /** At 1.5x and 2x the long title wraps and the cards grow; nothing is cut off. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun fontScale() = Frame(readyGridState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = Frame(readyGridState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(loadingGridState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun appending() = Frame(appendingGridState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun appendFailed() = Frame(appendFailedGridState())

    /** A filter that matches nothing, with the chip still there to clear it. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun empty() = Frame(emptyGridState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun emptySpanish() = Frame(emptyGridState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun error() = Frame(failedGridState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun errorSpanish() = Frame(failedGridState())
}

@Composable
private fun Frame(state: DiscoverGridUiState) {
    DiscoverGridScreen(kind = TitleKind.Movie, title = "Drama", state = state, actions = noGridActions)
}
