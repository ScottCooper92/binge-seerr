package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Every season of a show on one page, over the show's page. It opens at [seasonNumber], the season the show's page had chosen.
 * An episode goes to its own page through [onEpisode].
 */
@Composable
internal fun SeriesEpisodesEntry(
    tmdbId: Int,
    seriesName: String,
    seasonNumber: Int,
    onEpisode: (seasonNumber: Int, episodeNumber: Int) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<SeriesEpisodesViewModel, SeriesEpisodesViewModel.Factory>(
            key = "episodes-$tmdbId-$seasonNumber",
            creationCallback = { it.create(tmdbId, seasonNumber) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SeriesEpisodesScreen(
        title = seriesName,
        state = state,
        openAtSeason = seasonNumber,
        onSeasonNear = viewModel::onSeasonNear,
        onRetrySeason = viewModel::retrySeason,
        onEpisode = onEpisode,
        onBack = onBack,
        onRetry = viewModel::load,
    )
}
