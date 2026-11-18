package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeasonEpisodesScreen
import com.cooper.binge.companion.seerr.ui.discover.title.season.SeasonEpisodesUiState

private const val MANY_EPISODES = 24

@Composable
private fun Frame(
    state: SeasonEpisodesUiState,
    title: String = "Season 1",
) = SeasonEpisodesScreen(title, "No episodes listed for this season yet", state, onBack = {}, onRetry = {})

/** A whole season on a page of its own: the layout once across the device matrix, then each state on the phone. */
class SeasonEpisodesScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun ready() = Frame(SeasonEpisodesUiState.Ready(episodes(MANY_EPISODES)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(SeasonEpisodesUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = Frame(SeasonEpisodesUiState.Error(SeerrError.Unreachable))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun empty() = Frame(SeasonEpisodesUiState.Ready(emptyList()))

    /** The longest titles at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(SeasonEpisodesUiState.Ready(episodes(longNames = true)), "The Extended Special Collector's Edition Season")

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(SeasonEpisodesUiState.Ready(episodes(longNames = true)), "The Extended Special Collector's Edition Season")
}
