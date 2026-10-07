package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the television home shows: nothing until the store has answered, then setup, the sign-in again, or the rail. */
sealed interface TvHomeUiState {
    data object Loading : TvHomeUiState

    data object Setup : TvHomeUiState

    /** A server is saved but has rejected the session (#810): the sign-in takes the screen, with no rail behind it. */
    data object Reconnect : TvHomeUiState

    data object Connected : TvHomeUiState
}

/** The television home over the saved credentials: whether there is a server to put the rail in front of. */
@HiltViewModel
class TvHomeViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        val uiState: StateFlow<TvHomeUiState> =
            combine(connection.credentials, connection.sessionRejected) { credentials, rejected ->
                when {
                    credentials == null -> TvHomeUiState.Setup
                    rejected -> TvHomeUiState.Reconnect
                    else -> TvHomeUiState.Connected
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TvHomeUiState.Loading)

        /** Leaves a server that rejected the session, for one that is gone or that the user no longer uses. */
        fun disconnect() {
            viewModelScope.launch { connection.disconnect() }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
