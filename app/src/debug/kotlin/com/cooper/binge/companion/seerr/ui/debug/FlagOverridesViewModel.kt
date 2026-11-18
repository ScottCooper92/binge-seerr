package com.cooper.binge.companion.seerr.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.flags.AllFlagKeys
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FlagKey
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
    val flag: FlagKey,
    val effective: Boolean,
    val override: Boolean?,
) {
    /** The flag's key, which names it on the screen. */
    val key: String get() = flag.key
}

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
        featureFlags: FeatureFlags,
        private val overrides: FlagOverrides,
    ) : ViewModel() {
        val uiState: StateFlow<FlagOverridesUiState> =
            combine(featureFlags.flags, overrides.values) { flags, local ->
                FlagOverridesUiState.Ready(AllFlagKeys.map { FlagRow(it, flags[it], local[it.key]) })
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
            viewModelScope.launch { overrides.set(row.flag, next) }
        }
    }
