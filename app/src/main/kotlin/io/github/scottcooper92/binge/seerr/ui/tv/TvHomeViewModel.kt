package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the television home shows: nothing until the store has answered, then setup or the rail. */
sealed interface TvHomeUiState {
    data object Loading : TvHomeUiState

    data object Setup : TvHomeUiState

    data object Connected : TvHomeUiState
}

/** The television home over the saved credentials: whether there is a server to put the rail in front of. */
@HiltViewModel
class TvHomeViewModel
    @Inject
    constructor(
        connection: SeerrConnection,
    ) : ViewModel() {
        val uiState: StateFlow<TvHomeUiState> =
            connection.credentials
                .map { credentials -> if (credentials == null) TvHomeUiState.Setup else TvHomeUiState.Connected }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TvHomeUiState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
