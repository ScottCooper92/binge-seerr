package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
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

sealed interface SeasonEpisodesUiState {
    data object Loading : SeasonEpisodesUiState

    data class Error(
        val error: SeerrError,
    ) : SeasonEpisodesUiState

    data class Ready(
        val episodes: List<SeasonEpisode>,
    ) : SeasonEpisodesUiState
}

/** Every episode of one season. Detail keyed on an id: it loads once, and Retry reads it again. */
@HiltViewModel(assistedFactory = SeasonEpisodesViewModel.Factory::class)
class SeasonEpisodesViewModel
    @AssistedInject
    constructor(
        private val repository: SeriesDetailsRepository,
        @Assisted("tmdbId") private val tmdbId: Int,
        @Assisted("seasonNumber") private val seasonNumber: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("tmdbId") tmdbId: Int,
                @Assisted("seasonNumber") seasonNumber: Int,
            ): SeasonEpisodesViewModel
        }

        private val state = MutableStateFlow<SeasonEpisodesUiState>(SeasonEpisodesUiState.Loading)
        val uiState: StateFlow<SeasonEpisodesUiState> = state.asStateFlow()

        init {
            load()
        }

        fun load() {
            state.value = SeasonEpisodesUiState.Loading
            viewModelScope.launch {
                state.value =
                    attempt { repository.season(tmdbId, seasonNumber) }.fold(
                        onSuccess = { SeasonEpisodesUiState.Ready(it) },
                        onFailure = { SeasonEpisodesUiState.Error(it.toSeerrError()) },
                    )
            }
        }
    }
