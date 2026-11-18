package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 11, 18)

private val SET =
    DiscoverFilters(
        releaseFrom = "2020-03-01",
        ratingMin = 7.0,
        runtimeMin = 90,
        runtimeMax = 150,
        voteCountMin = 1500,
    )

/** The filter sheet's rows page, and the page a row opens, as the sheet shows them. */
class DiscoverFilterSheetScreenshotTest {
    /** The layout, once: the order chips, then a row for each filter with what it is set to. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun rows() = Rows(SET)

    /** Nothing set: every row says Any and Clear all is not offered. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun rowsUnset() = Rows(DiscoverFilters())

    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun rowsFont13() = Rows(SET)

    /** At 1.5x and 2x the values stay on one line and are cut with an ellipsis, never over the arrow. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun rowsFontScale() = Rows(SET)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun rowsSpanish() = Rows(SET)

    /** The release date page: the windows as chips, and the custom From and To fields. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun release() = Section(DiscoverFilterSection.Release)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun releaseSpanish() = Section(DiscoverFilterSection.Release)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun releaseFontScale() = Section(DiscoverFilterSection.Release)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun runtime() = Section(DiscoverFilterSection.Runtime)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun rating() = Section(DiscoverFilterSection.Rating)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun votes() = Section(DiscoverFilterSection.Votes)
}

@Composable
private fun Rows(filters: DiscoverFilters) {
    DiscoverFilterSheetContent(
        draft = DiscoverFilterDraft(DiscoverSort.PopularityDesc, filters),
        sortChoices = DiscoverSort.forKind(TitleKind.Movie),
        today = TODAY,
        onSort = {},
        onOpen = {},
        onClearAll = {},
    )
}

@Composable
private fun Section(section: DiscoverFilterSection) {
    Column {
        DiscoverSectionPage(section, SET, TODAY, onChange = {}, onDone = {})
    }
}
