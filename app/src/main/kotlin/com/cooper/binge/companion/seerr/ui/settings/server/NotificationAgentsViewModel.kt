package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.attempt
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val state = MutableStateFlow<AgentsUiState>(AgentsUiState.Loading)
        val uiState: StateFlow<AgentsUiState> = state.asStateFlow()

        fun reload() {
            state.value = AgentsUiState.Loading
            viewModelScope.launch(dispatcher) {
                attempt { load() }
                    .onSuccess { state.value = it }
                    .onFailure { state.value = AgentsUiState.Error(it.toSeerrError()) }
            }
        }

        private suspend fun load(): AgentsUiState.Ready =
            coroutineScope {
                val api = connection.api()
                val agents =
                    connection.profile().offeredAgents().map { agent ->
                        async { AgentSummary(agent, attempt { api.notificationAgent(agent.segment).enabled }.getOrNull()) }
                    }
                AgentsUiState.Ready(agents.awaitAll())
            }
    }
