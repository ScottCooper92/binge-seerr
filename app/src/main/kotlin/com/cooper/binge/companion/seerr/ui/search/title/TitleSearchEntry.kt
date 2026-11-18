package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.ui.openTitle
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType

/** The Search tab's root. A result opens in Binge where it is installed and on the server where it is not, until titles have pages. */
@Composable
fun TitleSearchEntry(
    modifier: Modifier = Modifier,
    viewModel: TitleSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val promoted = LocalFlags.current.bingePromotion
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TitleSearchEvent.Open -> {
                    val type = if (event.card.kind == TitleKind.Series) RequestMediaType.Tv else RequestMediaType.Movie
                    context.openTitle(promoted, type, event.card.tmdbId, event.card.webUrl)
                }
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
            ),
        modifier = modifier,
    )
}
