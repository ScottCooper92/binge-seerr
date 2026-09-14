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
import javax.inject.Inject

/**
 * Which screen the home shows: null while the answer is still being worked out, then whether a
 * server is saved.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        connection: SeerrConnection,
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

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
