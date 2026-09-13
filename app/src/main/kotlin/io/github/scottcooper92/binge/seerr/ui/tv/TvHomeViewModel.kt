package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the television home shows: nothing until the store has answered, then setup or the saved server. */
sealed interface TvHomeUiState {
    data object Loading : TvHomeUiState

    data object Setup : TvHomeUiState

    data class Connected(
        val serverUrl: String,
    ) : TvHomeUiState
}

/**
 * The television home over the saved credentials. It names the server rather than reading the hub, which
 * is a network round trip the connected plate has no use for.
 */
@HiltViewModel
class TvHomeViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        val uiState: StateFlow<TvHomeUiState> =
            connection.credentials
                .map { credentials -> if (credentials == null) TvHomeUiState.Setup else TvHomeUiState.Connected(credentials.baseUrl) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TvHomeUiState.Loading)

        fun disconnect() {
            viewModelScope.launch { connection.disconnect() }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
