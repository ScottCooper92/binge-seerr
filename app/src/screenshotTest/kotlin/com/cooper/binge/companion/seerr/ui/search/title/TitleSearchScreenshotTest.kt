package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalNavOverlayInsets
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview

private val RAIL = 80.dp

/** The Search tab: idle with recents, results, no results and a failure, at the sizes and in the languages the app ships. */
class TitleSearchScreenshotTest {
    /** The recents list, with one query too long for its row. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun idleWithRecents() = Frame(idleWithRecentsState())

    /** A landscape tablet, where the navigation rail overlays the start edge: the recents and the rails clear it once, side by side. */
    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun idleWithRecentsRail() =
        CompositionLocalProvider(LocalNavOverlayInsets provides PaddingValues(start = RAIL)) { Frame(idleWithRecentsState()) }

    /** The trending rails still on their way under the recents. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun idleLoading() = Frame(idleLoadingState())

    /** A type chosen: only its results are listed, and the chip row keeps every count. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun resultsMovies() = Frame(readyMoviesState())

    /** Nothing typed, nothing remembered and no trending: the prompt. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun idleWithoutRecents() = Frame(idleWithoutRecentsState())

    /** Every status and kind in one list, a person with no click, and a title long enough to wrap. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun results() = Frame(readyResultsState())

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun resultsTablet() = Frame(readyResultsState())

    /** The same list at 1.3x, where a phone's larger text commonly lands. */
    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun resultsFont13() = Frame(readyResultsState())

    /** At 2x the long title wraps and the rows grow; nothing is cut off. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun resultsFontScale() = Frame(readyResultsState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun resultsSpanish() = Frame(readyResultsState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun idleSpanish() = Frame(idleWithRecentsState())

    /** A chosen group that none of the loaded results belong to, with no pages left: the message, not a blank screen. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun emptyGroup() = Frame(readyEmptyTypeState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun emptyGroupSpanish() = Frame(readyEmptyTypeState())

    /** The same group while the next page is on its way. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun emptyGroupLoading() = Frame(readyEmptyTypeLoadingState())

    /** A further page loading under the list. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun appending() = Frame(readyAppendingState())

    /** A further page that failed, with its own retry under the rows that did load. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun appendFailed() = Frame(readyAppendFailedState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun noResults() = Frame(noResultsState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun noResultsSpanish() = Frame(noResultsState())

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun error() = Frame(failedState())

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun errorSpanish() = Frame(failedState())
}

@Composable
private fun Frame(state: TitleSearchUiState) {
    TitleSearchScreen(state = state, actions = noActions)
}
