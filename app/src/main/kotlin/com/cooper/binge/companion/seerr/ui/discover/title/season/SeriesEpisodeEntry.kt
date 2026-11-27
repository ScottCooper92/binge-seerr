package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.discover.request.messageRes

/** One episode of a show, over the season it is in. The request sheet asks for that season only. */
@Composable
internal fun SeriesEpisodeEntry(
    tmdbId: Int,
    seriesName: String,
    seasonNumber: Int,
    episodeNumber: Int,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<SeriesEpisodeViewModel, SeriesEpisodeViewModel.Factory>(
            key = "episode-$tmdbId-$seasonNumber-$episodeNumber",
            creationCallback = { it.create(tmdbId, seasonNumber, episodeNumber) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SeriesEpisodeEvent.Requested -> snackbar.showSnackbar(resources.getString(event.result.messageRes()))
            }
        }
    }
    SeriesEpisodeScreen(
        title = seriesName,
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onRetry = viewModel::load,
        onRequest = viewModel::openRequestSheet,
        onRequestSheetClose = viewModel::closeRequestSheet,
        onRequested = viewModel::requested,
    )
}
