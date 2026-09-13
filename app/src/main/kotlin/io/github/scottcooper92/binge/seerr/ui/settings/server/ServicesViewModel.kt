package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrOverrideRuleDto
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) : ViewModel() {
        private val state = MutableStateFlow<ServicesUiState>(ServicesUiState.Loading)
        val uiState: StateFlow<ServicesUiState> = state.asStateFlow()

        fun reload() {
            state.value = ServicesUiState.Loading
            viewModelScope.launch {
                runCatching { load() }
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
