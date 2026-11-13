package com.cooper.binge.companion.seerr.ui.settings.licences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The licences page's state: loading, the list, or a list that could not be read. */
sealed interface LicencesUiState {
    data object Loading : LicencesUiState

    data class Ready(
        val entries: List<LicenceEntry>,
    ) : LicencesUiState

    data object Unreadable : LicencesUiState
}

/** Reads the shipped libraries' licences once for the page. */
@HiltViewModel
class LicencesViewModel
    @Inject
    constructor(
        source: LicenceSource,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<LicencesUiState>(LicencesUiState.Loading)
        val uiState: StateFlow<LicencesUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                _uiState.value =
                    attempt { source.load() }.fold(
                        onSuccess = { LicencesUiState.Ready(it) },
                        onFailure = { LicencesUiState.Unreadable },
                    )
            }
        }
    }
