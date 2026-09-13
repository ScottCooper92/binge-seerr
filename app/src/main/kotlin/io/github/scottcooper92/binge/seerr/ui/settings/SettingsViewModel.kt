package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings: the connection, the server, and the admin's read-only view of its configuration. Every
 * fetch re-runs on the screen becoming visible, so returning from Edit connection shows the new
 * server; each flow's null seed holds the previous value while a refetch is in flight rather than
 * blanking its rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val loader: SettingsLoader,
    ) : ViewModel() {
        private val fetchTrigger = MutableStateFlow(0)

        private val summary: Flow<ConnectionSummary?> =
            fetchTrigger.flatMapLatest { flow { emit(runCatching { loader.connection() }.getOrNull()) } }.onStart { emit(null) }

        private val server: Flow<ServerSummary?> =
            fetchTrigger.flatMapLatest { flow { emit(runCatching { loader.server() }.getOrNull()) } }.onStart { emit(null) }

        private val config: Flow<ServerConfig?> =
            fetchTrigger.flatMapLatest { flow { emit(loader.config()) } }.onStart { emit(null) }

        val uiState: StateFlow<SettingsUiState> =
            combine(summary, server, config) { summary, server, config ->
                if (summary == null || server == null) SettingsUiState.Loading else SettingsUiState.Ready(summary, server, config)
            }.stateIn(viewModelScope, SharingStarted.Lazily, SettingsUiState.Loading)

        fun setScreenVisible(visible: Boolean) {
            if (visible) fetchTrigger.value++
        }

        fun disconnect() {
            viewModelScope.launch { connection.disconnect() }
        }
    }
