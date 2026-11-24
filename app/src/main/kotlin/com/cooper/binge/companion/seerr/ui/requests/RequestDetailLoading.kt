package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** One request's detail, read through the view model its own sheet shares, so a list and a sheet fetch it once. Null while it loads. */
@Composable
private fun rememberRequestDetailFromViewModel(requestId: Int): RequestDetail? {
    val viewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(
            key = "request-detail-$requestId",
            creationCallback = { factory -> factory.create(requestId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    return (state as? RequestDetailUiState.Ready)?.detail
}

/** What the nav host provides as [LocalRequestDetail]. One value for the whole session, so providing it never recomposes what reads it. */
internal val RequestDetailFromViewModel: @Composable (Int) -> RequestDetail? =
    { requestId -> rememberRequestDetailFromViewModel(requestId) }
