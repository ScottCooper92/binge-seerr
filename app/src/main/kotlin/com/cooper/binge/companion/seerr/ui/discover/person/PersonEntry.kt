package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.binge.companion.seerr.ui.shareLink

/** A person, a page of its own. A title opens through [LocalOpenTitle], as from any other card. */
@Composable
internal fun PersonEntry(
    personId: Int,
    onBack: () -> Unit,
) {
    val viewModel = hiltViewModel<PersonPageViewModel, PersonPageViewModel.Factory>(creationCallback = { it.create(personId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openTitle = LocalOpenTitle.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val refreshFailed = stringResource(R.string.person_refresh_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PersonPageEvent.RefreshFailed -> snackbar.showSnackbar(refreshFailed)
            }
        }
    }
    PersonPageScreen(
        state = state,
        snackbarHostState = snackbar,
        actions =
            PersonPageActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onRetryCredits = viewModel::retryCredits,
                onRefresh = viewModel::refresh,
                onShare = context::shareLink,
                onOpenUrl = context::openInBrowser,
                onOpenTitle = { kind, id -> openTitle(kind, id, "") },
            ),
    )
}
