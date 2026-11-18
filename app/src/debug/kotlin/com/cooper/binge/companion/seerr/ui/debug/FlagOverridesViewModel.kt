package com.cooper.binge.companion.seerr.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.flags.DISCOVERY_FLAG_KEY
import com.cooper.binge.companion.seerr.flags.DiscoveryFlag
import com.cooper.binge.companion.seerr.flags.DiscoveryFlagOverride
import com.cooper.binge.companion.seerr.flags.SEARCH_TOGGLES_FLAG_KEY
import com.cooper.binge.companion.seerr.flags.SearchTogglesFlag
import com.cooper.binge.companion.seerr.flags.SearchTogglesOverride
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.Flag
import com.cooper.homelab.core.flags.FlagOverrides
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One flag on the developer screen: what it resolves to now, and the override behind that, if any. */
data class FlagRow(
    val key: String,
    /** The shared flag this row is, or null for one of the app's own. */
    val flag: Flag?,
    val effective: Boolean,
    val override: Boolean?,
)

sealed interface FlagOverridesUiState {
    data object Loading : FlagOverridesUiState

    data class Ready(
        val rows: List<FlagRow>,
    ) : FlagOverridesUiState
}

/** Debug builds only: the flags and the local overrides that win over Remote Config. */
@HiltViewModel
class FlagOverridesViewModel
    @Inject
    constructor(
        private val featureFlags: FeatureFlags,
        private val overrides: FlagOverrides,
        private val discoveryFlag: DiscoveryFlag,
        private val discoveryOverride: DiscoveryFlagOverride,
        private val searchTogglesFlag: SearchTogglesFlag,
        private val searchTogglesOverride: SearchTogglesOverride,
    ) : ViewModel() {
        val uiState: StateFlow<FlagOverridesUiState> =
            combine(
                combine(featureFlags.flags, overrides.values, ::Pair),
                combine(discoveryFlag.enabled, discoveryOverride.value, ::Pair),
                combine(searchTogglesFlag.enabled, searchTogglesOverride.value, ::Pair),
            ) { (flags, local), (discovery, discoveryLocal), (toggles, togglesLocal) ->
                FlagOverridesUiState.Ready(
                    Flag.entries.map { FlagRow(it.key, it, flags[it], local[it]) } +
                        listOf(FlagRow(DISCOVERY_FLAG_KEY, null, discovery, discoveryLocal)) +
                        listOf(FlagRow(SEARCH_TOGGLES_FLAG_KEY, null, toggles, togglesLocal)),
                )
            }.map<FlagOverridesUiState.Ready, FlagOverridesUiState> { it }
                .stateIn(viewModelScope, SharingStarted.Lazily, FlagOverridesUiState.Loading)

        /** Default, then on, then off, then default again. */
        fun cycle(row: FlagRow) {
            val next =
                when (row.override) {
                    null -> true
                    true -> false
                    false -> null
                }
            viewModelScope.launch {
                when {
                    row.flag != null -> overrides.set(row.flag, next)
                    row.key == SEARCH_TOGGLES_FLAG_KEY -> searchTogglesOverride.set(next)
                    else -> discoveryOverride.set(next)
                }
            }
        }
    }
