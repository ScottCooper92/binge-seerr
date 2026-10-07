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
 * Which screen the home shows: null while the answer is still being worked out, then whether a
 * server is saved, and whether that server still takes the saved session.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        restore: ConnectionRestore,
    ) : ViewModel() {
        val isConnected: StateFlow<Boolean?> =
            combine(connection.credentials, restore.settled) { saved, settled ->
                // Nothing saved is only an answer once a restore has been tried. A device that has
                // just been transferred has a connection coming, and setup would ask for it again.
                when {
                    saved != null -> true
                    settled -> false
                    else -> null
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        /** The saved server has rejected the session (#810): sign-in takes the window in place of the screens. */
        val sessionRejected: StateFlow<Boolean> =
            connection.sessionRejected.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

        /** Leaves a server that rejected the session, for one that is gone or that the user no longer uses. */
        fun disconnect() {
            viewModelScope.launch { connection.disconnect() }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
