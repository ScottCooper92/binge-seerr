package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.preview.PreviewSystemBarInsets
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.HubAccount

private const val MID_LIST = 9

/** The "see all" grid: its order and the filter the row started with, over posters, and its empty, loading and failed states. */
class DiscoverGridScreenshotTest {
    /** The layout, once: the order chip and the row's own filter, over a grid with every status. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun filtered() = Frame(readyGridState())

    /** The account beside the Filters pill in the large title row (#2366). */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withAccount() = DiscoverGridScreen(TitleKind.Movie, "Drama", readyGridState(), noGridActions, accountBar = ACCOUNT)

    /** The same grid at tablet width, in more columns. */
    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun filteredTablet() = Frame(readyGridState())

    /** The filter cleared: only the order chip is left. */
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

    /** At 2x the long title wraps and the cards grow; nothing is cut off. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun fontScale() = Frame(readyGridState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun spanish() = Frame(readyGridState())

    /**
     * Scrolled mid-list under a status bar and a navigation bar: posters pass under the bar and run down under the gesture bar. The bar's
     * scrim fades in with a real scroll gesture, which a frame does not make, so the title shows over the posters here.
     */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scrolledUnderBars() =
        PreviewSystemBarInsets {
            DiscoverGridScreen(
                TitleKind.Movie,
                "Drama",
                readyGridState(),
                noGridActions,
                firstVisibleIndex = MID_LIST,
            )
        }

    /** The row's own chip, then one chip for each filter added in the sheet, each with what it is set to. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addedFilters() = Frame(addedFiltersGridState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun addedFiltersSpanish() = Frame(addedFiltersGridState())

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

    /** The same, at the largest font. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun emptyLargeText() = Frame(emptyGridState())

    /** An empty watchlist says what fills it. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun emptyWatchlist() = Frame(emptyWatchlistState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun emptyWatchlistSpanish() = Frame(emptyWatchlistState())

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun emptyWatchlistLargeText() = Frame(emptyWatchlistState())

    /** A list that is empty by itself, with no filter of the user's to clear. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun emptyList() = Frame(emptyListState())

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

private val ACCOUNT = AccountBar(HubAccount(id = 1, name = "Ada Lovelace", isAdmin = true, avatarUrl = null), quota = null)
