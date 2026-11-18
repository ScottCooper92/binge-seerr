package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel

/** The whole season of a show, a page of its own over the show's page. */
@Composable
internal fun SeasonEpisodesEntry(
    tmdbId: Int,
    seasonNumber: Int,
    seasonName: String,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<SeasonEpisodesViewModel, SeasonEpisodesViewModel.Factory>(
            key = "season-$tmdbId-$seasonNumber",
            creationCallback = { it.create(tmdbId, seasonNumber) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SeasonEpisodesScreen(
        title = seasonLabel(seasonNumber, seasonName),
        emptyMessage = stringResource(R.string.season_episodes_empty),
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
    )
}
