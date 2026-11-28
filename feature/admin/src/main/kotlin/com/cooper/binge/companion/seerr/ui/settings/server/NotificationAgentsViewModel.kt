package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
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
        agentChanges: NotificationAgentChanges = NotificationAgentChanges(),
    ) : ViewModel() {
        /** Each read replaces the one before it, so a slow read started earlier cannot land over a later one. */
        private var loading: Job? = null

        private val state = MutableStateFlow<AgentsUiState>(AgentsUiState.Loading)
        val uiState: StateFlow<AgentsUiState> = state.asStateFlow()

        init {
            // Said once an agent's write has landed, so this read goes out after it and sees it (#1556). Collected on Main,
            // where the page calls reload() and refresh() too, so [loading] has one writer at a time; read() moves the network.
            viewModelScope.launch { agentChanges.changes.collect { refresh() } }
        }

        fun reload() {
            state.value = AgentsUiState.Loading
            read { state.value = AgentsUiState.Error(it.toSeerrError()) }
        }

        /** Reads again without going back to Loading, for a page that is showing the list while an agent is changed under it. */
        fun refresh() =
            read {
                // A reload this replaced was still loading, and nothing else will answer for it.
                if (state.value is AgentsUiState.Loading) state.value = AgentsUiState.Error(it.toSeerrError())
            }

        private fun read(onFailure: (Throwable) -> Unit) {
            loading?.cancel()
            loading =
                viewModelScope.launch(dispatcher) {
                    attempt { load() }
                        .onSuccess { state.value = it }
                        .onFailure(onFailure)
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
