package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeasonEpisodesState
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeasonSection
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeriesEpisodesScreen
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeriesEpisodesUiState

private const val THREE_EPISODES = 3
private const val MANY_EPISODES = 8

@Composable
private fun Frame(
    state: SeriesEpisodesUiState,
    openAt: Int = 1,
    title: String = "The Long Winter",
) = SeriesEpisodesScreen(title, state, openAt, onSeasonNear = {}, onRetrySeason = {}, onBack = {}, onRetry = {})

private fun section(
    number: Int,
    episodes: SeasonEpisodesState,
) = SeasonSection(season(number, TitleAvailability.None), episodes)

private fun loaded(
    count: Int = THREE_EPISODES,
    longNames: Boolean = false,
) = SeasonEpisodesState.Loaded(episodes(count, longNames))

/** Every season of a show on one page: the layout once across the device matrix, then each state on the phone. */
class SeriesEpisodesScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun ready() = Frame(SeriesEpisodesUiState.Ready(listOf(section(1, loaded()), section(2, loaded()), section(3, loaded()))))

    /** The page opened at the second season, long enough to scroll, with the one before it already read and the one after it not. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun openedAtSecondSeason() =
        Frame(
            SeriesEpisodesUiState.Ready(
                listOf(section(1, loaded(1)), section(2, loaded(MANY_EPISODES)), section(3, SeasonEpisodesState.Loading)),
            ),
            openAt = 2,
        )

    /** Seasons not read yet each hold a skeleton row per episode, so the list keeps its length as they load. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seasonsReading() =
        Frame(SeriesEpisodesUiState.Ready(listOf(section(1, SeasonEpisodesState.Loading), section(2, SeasonEpisodesState.Loading))))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seasonFailed() =
        Frame(SeriesEpisodesUiState.Ready(listOf(section(1, loaded()), section(2, SeasonEpisodesState.Failed(SeerrError.Unreachable)))))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seasonEmpty() = Frame(SeriesEpisodesUiState.Ready(listOf(section(1, loaded(count = 0)), section(2, loaded()))))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(SeriesEpisodesUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = Frame(SeriesEpisodesUiState.Error(SeerrError.Unreachable))

    /** The longest names and a long show name at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() =
        Frame(
            SeriesEpisodesUiState.Ready(listOf(section(1, loaded(longNames = true)), section(2, loaded()))),
            title = "The Extended Special Collector's Edition Of The Long Winter",
        )

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() =
        Frame(
            SeriesEpisodesUiState.Ready(listOf(section(1, loaded(longNames = true)), section(2, loaded()))),
            title = "The Extended Special Collector's Edition Of The Long Winter",
        )
}
