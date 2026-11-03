package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrOverrideRuleDto
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The services page: every Radarr and Sonarr instance, and the override rules where the server has
 * them. Read on every arrival, since an instance or a rule is edited on a page of its own.
 */
@HiltViewModel
class ServicesViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val state = MutableStateFlow<ServicesUiState>(ServicesUiState.Loading)
        val uiState: StateFlow<ServicesUiState> = state.asStateFlow()

        private val failures = MutableSharedFlow<SeerrError>(extraBufferCapacity = 1)

        /** One event per failed pull, for the screen's usual message. */
        val reloadFailed: SharedFlow<SeerrError> = failures.asSharedFlow()

        /** A pull: re-reads the page and swaps it in, keeping what shows until then. A failed read leaves the page as it is. */
        fun refresh() {
            val ready = state.value as? ServicesUiState.Ready ?: return
            if (ready.isRefreshing) return
            state.value = ready.copy(isRefreshing = true)
            viewModelScope.launch(dispatcher) {
                attempt { load() }
                    .onSuccess { state.value = it }
                    .onFailure {
                        state.update { current -> (current as? ServicesUiState.Ready)?.copy(isRefreshing = false) ?: current }
                        failures.tryEmit(it.toSeerrError())
                    }
            }
        }

        fun reload() {
            state.value = ServicesUiState.Loading
            viewModelScope.launch(dispatcher) {
                attempt { load() }
                    .onSuccess { state.value = it }
                    .onFailure { state.value = ServicesUiState.Error(it.toSeerrError()) }
            }
        }

        private suspend fun load(): ServicesUiState.Ready =
            coroutineScope {
                val api = connection.api()
                val hasRules = async { connection.profile().hasOverrideRules }
                val radarr = async { api.radarrSettings() }
                val sonarr = async { api.sonarrSettings() }
                val rules = async { if (hasRules.await()) api.overrideRules() else null }
                val instances =
                    radarr.await().mapNotNull { it.toSummary(ServiceType.Radarr) } +
                        sonarr.await().mapNotNull { it.toSummary(ServiceType.Sonarr) }
                ServicesUiState.Ready(instances = instances, rules = rules.await()?.map { it.toSummary(instances) })
            }
    }

/** A rule names its instance by id; the summary names it by name, or by id where the instance is gone. */
internal fun SeerrOverrideRuleDto.toSummary(instances: List<DvrSummary>): OverrideRuleSummary {
    val type = if (radarrServiceId != null) ServiceType.Radarr else ServiceType.Sonarr
    val serviceId = radarrServiceId ?: sonarrServiceId
    val instance = instances.firstOrNull { it.type == type && it.id == serviceId }
    return OverrideRuleSummary(
        id = id ?: 0,
        instanceName = instance?.name ?: "$type $serviceId",
        conditions = listOf(users, genre, language, keywords).count { !it.isNullOrBlank() },
    )
}
