package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The agents page: every agent this server has, each with whether it is on. Read on every arrival. */
@HiltViewModel
class NotificationAgentsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) : ViewModel() {
        private val state = MutableStateFlow<AgentsUiState>(AgentsUiState.Loading)
        val uiState: StateFlow<AgentsUiState> = state.asStateFlow()

        fun reload() {
            state.value = AgentsUiState.Loading
            viewModelScope.launch {
                runCatching { load() }
                    .onSuccess { state.value = it }
                    .onFailure { state.value = AgentsUiState.Error(it.toSeerrError()) }
            }
        }

        private suspend fun load(): AgentsUiState.Ready =
            coroutineScope {
                val api = connection.api()
                val agents =
                    connection.profile().offeredAgents().map { agent ->
                        async { AgentSummary(agent, runCatching { api.notificationAgent(agent.segment).enabled }.getOrNull()) }
                    }
                AgentsUiState.Ready(agents.awaitAll())
            }
    }
