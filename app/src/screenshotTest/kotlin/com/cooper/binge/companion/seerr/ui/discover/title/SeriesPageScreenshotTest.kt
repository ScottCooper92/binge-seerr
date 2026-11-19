package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.preview.SeerrExpandedFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrExpandedSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrNarrowTabletPreview
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview
import kotlinx.coroutines.flow.emptyFlow

private const val EPISODES_PX = 1900
private const val LARGE_FONT_EPISODES_PX = 4300
private const val WATCH_PX = 4200
private const val FACTS_PX = 1800
private const val FACTS_LARGE_FONT_PX = 3600

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

private val NO_SEASON_ACTIONS = SeasonActions({}, {}, { _, _ -> })

/** As [NO_ACTIONS], but a network opens its grid, so the networks are links in the accent colour. */
private val LINKED_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onOpenCompany = {})

@Composable
private fun Frame(state: SeriesPageUiState) = SeriesPageScreen(state, emptyFlow(), NO_ACTIONS, NO_SEASON_ACTIONS)

/** The series page: the layout once across the device matrix, then each state of it on the phone. */
class SeriesPageScreenshotTest {
    /** A partly available show with a season still open, so the button stays. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun requestable() = Frame(SeriesPageUiState.Ready(fullSeries(), seriesExtras()))

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun requestableTablet() = Frame(SeriesPageUiState.Ready(fullSeries(), seriesExtras()))

    /** A window wider than the reading column but short of the expanded line: the sections share one left edge. */
    @PreviewTest
    @SeerrNarrowTabletPreview
    @Composable
    fun requestableNarrowTablet() = Frame(SeriesPageUiState.Ready(fullSeries(), seriesExtras()))

    @PreviewTest
    @SeerrExpandedSpanishPreviews
    @Composable
    fun requestableExpandedSpanish() = Frame(SeriesPageUiState.Ready(fullSeries(), seriesExtras()))

    @PreviewTest
    @SeerrExpandedFontScalePreviews
    @Composable
    fun requestableExpandedLargeText() = Frame(SeriesPageUiState.Ready(fullSeries(), seriesExtras()))

    /** The season picker sheet's content: one season in each state, the status beside the ones the server holds, the shown one checked. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seasonPicker() = SeasonPickerContent(fullSeries().seasons, selected = 1, onSelect = {})

    /** The first season selected: its first five episodes, then the row that opens the rest, reached by scrolling. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun episodes() =
        SeriesPageScreen(
            readyWithEpisodes(),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = EPISODES_PX),
        )

    /** A season's episodes are being read. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun episodesLoading() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(fullSeries(), seriesExtras(), selectedSeason = 1, episodes = mapOf(1 to EpisodesState.Loading)),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = EPISODES_PX),
        )

    /** A season's episodes failed to read: the failure stays in place with its Retry. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun episodesError() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(
                fullSeries(),
                seriesExtras(),
                selectedSeason = 1,
                episodes = mapOf(1 to EpisodesState.Error(com.cooper.binge.companion.seerr.seerr.SeerrError.Unreachable)),
            ),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = EPISODES_PX),
        )

    /** Long episode titles under a long season name at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun episodesLargeFonts() =
        SeriesPageScreen(
            readyWithEpisodes(longSeries(), longNames = true),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = LARGE_FONT_EPISODES_PX),
        )

    /** The facts with the networks as links, in the accent colour. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun factsLinked() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(fullSeries().copy(companiesOpen = true), seriesExtras()),
            emptyFlow(),
            LINKED_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = FACTS_PX),
        )

    /** Where to watch on the series page: both kinds, the Stream chip chosen, and the JustWatch credit. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun whereToWatch() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(fullSeries(), seriesExtras()),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = WATCH_PX),
        )

    /** Every season held or asked for: no Request button. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun allCovered() =
        Frame(
            SeriesPageUiState.Ready(
                fullSeries(
                    seasons = mixedSeasons().dropLast(1),
                    standard = VersionState(TitleAvailability.Available, mayRequest = false),
                ),
                seriesExtras(),
            ),
        )

    /** The sections that load apart have not arrived yet. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun extrasPending() = Frame(SeriesPageUiState.Ready(fullSeries()))

    /** The server knows almost nothing: every empty section is left out. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun sparse() = Frame(SeriesPageUiState.Ready(sparseSeries()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(SeriesPageUiState.Loading)

    /** The longest names at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(SeriesPageUiState.Ready(longSeries(), seriesExtras()))

    /** The season picker under the longest names at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun seasonPickerLargeFonts() = SeasonPickerContent(longSeries().seasons, selected = 2, onSelect = {})

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(SeriesPageUiState.Ready(longSeries(), seriesExtras()))

    /** The facts list at font scale 2, below the episodes. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun factsLargeFonts() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(longSeries(), seriesExtras()),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = FACTS_LARGE_FONT_PX),
        )

    /** The facts list in Spanish: the labels, the dates and the next episode. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun factsSpanish() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(longSeries(), seriesExtras()),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = FACTS_PX),
        )
}
