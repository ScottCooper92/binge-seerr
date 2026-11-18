package com.cooper.binge.companion.seerr.ui.discover.title.credits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.TitleKind

/** The cast and crew of one title, a page of its own over the title's page. */
@Composable
internal fun TitleCreditsEntry(
    kind: TitleKind,
    tmdbId: Int,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<TitleCreditsViewModel, TitleCreditsViewModel.Factory>(
            key = "credits-$kind-$tmdbId",
            creationCallback = { it.create(kind, tmdbId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TitleCreditsScreen(state = state, onBack = onBack, onRetry = viewModel::load)
}
