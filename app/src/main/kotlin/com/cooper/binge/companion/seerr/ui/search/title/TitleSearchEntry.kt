package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle

/** The Search tab's root. A result opens the title's page, or with the flag off the hand-off [LocalOpenTitle] falls back to. */
@Composable
fun TitleSearchEntry(
    modifier: Modifier = Modifier,
    viewModel: TitleSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openTitle by rememberUpdatedState(LocalOpenTitle.current)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TitleSearchEvent.Open -> openTitle(event.card.kind, event.card.tmdbId, event.card.webUrl)
            }
        }
    }
    TitleSearchScreen(
        state = state,
        actions =
            TitleSearchActions(
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::onClear,
                onSubmit = viewModel::onSubmit,
                onRetry = viewModel::onRetry,
                onLoadMore = viewModel::onLoadMore,
                onRecent = viewModel::onRecent,
                onForgetRecents = viewModel::onForgetRecents,
                onOpenTitle = viewModel::onOpenTitle,
                onType = viewModel::onType,
            ),
        modifier = modifier,
    )
}
