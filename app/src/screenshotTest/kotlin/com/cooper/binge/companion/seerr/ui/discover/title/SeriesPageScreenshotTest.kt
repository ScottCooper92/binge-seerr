package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import kotlinx.coroutines.flow.emptyFlow

private const val SEASONS_SCROLLED_PX = 900
private const val LARGE_FONT_SEASONS_PX = 2300
private const val EPISODES_PX = 1900

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

private val NO_SEASON_ACTIONS = SeasonActions({}, {}, { _, _ -> })

@Composable
private fun Frame(state: SeriesPageUiState) = SeriesPageScreen(state, emptyFlow(), NO_ACTIONS, NO_SEASON_ACTIONS)

/** The series page: the layout once across the device matrix, then each state of it on the phone. */
class SeriesPageScreenshotTest {
    /** A partly available show with a season still open, so the button stays. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun requestable() = Frame(SeriesPageUiState.Ready(fullSeries(), seriesExtras()))

    /** The season list reached by scrolling: one season in each state, the status beside the ones the server holds. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seasonList() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(fullSeries(), seriesExtras()),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = SEASONS_SCROLLED_PX),
        )

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

    /** Long episode titles under a long season name at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun episodesLargeFonts() =
        SeriesPageScreen(
            readyWithEpisodes(longSeries(), longNames = true),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = EPISODES_PX),
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

    /** The longest names at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(SeriesPageUiState.Ready(longSeries(), seriesExtras()))

    /** The season list under the longest names at font scales 1.5 and 2, reached by scrolling. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun seasonsLargeFonts() =
        SeriesPageScreen(
            SeriesPageUiState.Ready(longSeries(), seriesExtras()),
            emptyFlow(),
            NO_ACTIONS,
            NO_SEASON_ACTIONS,
            scrollState = rememberScrollState(initial = LARGE_FONT_SEASONS_PX),
        )

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(SeriesPageUiState.Ready(longSeries(), seriesExtras()))
}
