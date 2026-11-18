package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle

/** The page of one collection, with its films opening through the nav host's title hand-off. */
@Composable
internal fun CollectionPageEntry(
    id: Int,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<CollectionPageViewModel, CollectionPageViewModel.Factory>(creationCallback = { it.create(id) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openTitle = LocalOpenTitle.current
    CollectionPageScreen(
        state = state,
        actions = CollectionPageActions(onBack, viewModel::load) { openTitle(it.kind, it.tmdbId, "") },
    )
}
