package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle

/** A person, a page of its own. A title opens through [LocalOpenTitle], as from any other card. */
@Composable
internal fun PersonEntry(
    personId: Int,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<PersonPageViewModel, PersonPageViewModel.Factory>(creationCallback = { it.create(personId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openTitle = LocalOpenTitle.current
    PersonPageScreen(
        state = state,
        actions =
            PersonPageActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onRetryCredits = viewModel::retryCredits,
                onFilter = viewModel::setFilter,
                onOpenTitle = { kind, id -> openTitle(kind, id, "") },
            ),
    )
}
