package com.cooper.binge.companion.seerr.ui.discover.title.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.title.TitleCredits
import com.cooper.binge.companion.seerr.discover.title.TitleCreditsRepository
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TitleCreditsUiState {
    data object Loading : TitleCreditsUiState

    data class Error(
        val error: SeerrError,
    ) : TitleCreditsUiState

    data class Ready(
        val credits: TitleCredits,
    ) : TitleCreditsUiState
}

/** The whole cast and crew of one title. Detail keyed on an id: it loads once, and Retry reads it again. */
@HiltViewModel(assistedFactory = TitleCreditsViewModel.Factory::class)
class TitleCreditsViewModel
    @AssistedInject
    constructor(
        private val repository: TitleCreditsRepository,
        @Assisted private val kind: TitleKind,
        @Assisted private val tmdbId: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                kind: TitleKind,
                tmdbId: Int,
            ): TitleCreditsViewModel
        }

        private val state = MutableStateFlow<TitleCreditsUiState>(TitleCreditsUiState.Loading)
        val uiState: StateFlow<TitleCreditsUiState> = state.asStateFlow()

        init {
            load()
        }

        fun load() {
            state.value = TitleCreditsUiState.Loading
            viewModelScope.launch {
                state.value =
                    attempt { repository.credits(kind, tmdbId) }.fold(
                        onSuccess = { TitleCreditsUiState.Ready(it) },
                        onFailure = { TitleCreditsUiState.Error(it.toSeerrError()) },
                    )
            }
        }
    }
