package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.ConnectionRestore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the phone's home shows: nothing until the answer is worked out, then setup, the connected screens, or the
 * sign-in again. The television's twin is [io.github.scottcooper92.binge.seerr.ui.tv.TvHomeUiState].
 */
sealed interface HomeUiState {
    /** Nothing saved is only an answer once a restore has been tried, so until then the screen is a spinner. */
    data object Resolving : HomeUiState

    data object Setup : HomeUiState

    data object Connected : HomeUiState

    /** A server is saved but has rejected the session (#810): the sign-in takes the window in place of the screens. */
    data object Reconnect : HomeUiState
}

/** Which screen the home shows, from the saved server, whether a restore has been tried, and whether it takes the session. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        restore: ConnectionRestore,
    ) : ViewModel() {
        val uiState: StateFlow<HomeUiState> =
            combine(connection.credentials, restore.settled, connection.sessionRejected) { saved, settled, rejected ->
                // Nothing saved is only an answer once a restore has been tried. A device that has
                // just been transferred has a connection coming, and setup would ask for it again.
                when {
                    rejected -> HomeUiState.Reconnect
                    saved != null -> HomeUiState.Connected
                    settled -> HomeUiState.Setup
                    else -> HomeUiState.Resolving
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Resolving)

        /** Leaves a server that rejected the session, for one that is gone or that the user no longer uses. */
        fun disconnect() {
            viewModelScope.launch { connection.disconnect() }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
