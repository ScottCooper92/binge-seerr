package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.discover.TitleResult

/** The Search tab's root. A result that opens goes to [onOpenTitle], which the app's navigation supplies. */
@Composable
fun TitleSearchEntry(
    onOpenTitle: (TitleResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TitleSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                onOpenTitle = onOpenTitle,
            ),
        modifier = modifier,
    )
}
