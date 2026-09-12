package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Which screen the home shows: null until the store has answered, then whether a server is saved. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        connection: SeerrConnection,
    ) : ViewModel() {
        val isConnected: StateFlow<Boolean?> =
            connection.credentials
                .map { it != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
