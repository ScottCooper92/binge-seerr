package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SeriesEpisodesUiState {
    data object Loading : SeriesEpisodesUiState

    data class Error(
        val error: SeerrError,
    ) : SeriesEpisodesUiState

    data class Ready(
        val seasons: List<SeasonSection>,
    ) : SeriesEpisodesUiState
}

/** One season of the page: its header's facts and its episodes, which load when the season scrolls near. */
data class SeasonSection(
    val season: SeasonInfo,
    val episodes: SeasonEpisodesState,
)

sealed interface SeasonEpisodesState {
    data object Loading : SeasonEpisodesState

    data class Loaded(
        val episodes: List<SeasonEpisode>,
    ) : SeasonEpisodesState

    data class Failed(
        val error: SeerrError,
    ) : SeasonEpisodesState
}

/**
 * Every season of a show on one page. The seasons come from the show's details, specials last, and each season's episodes are read
 * once, when the page asks: the chosen season at once, the others as they scroll near. Detail keyed on an id.
 */
@HiltViewModel(assistedFactory = SeriesEpisodesViewModel.Factory::class)
class SeriesEpisodesViewModel
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
            ): SeriesEpisodesViewModel
        }

        private val state = MutableStateFlow<SeriesEpisodesUiState>(SeriesEpisodesUiState.Loading)
        val uiState: StateFlow<SeriesEpisodesUiState> = state.asStateFlow()
        private val requested = mutableSetOf<Int>()

        init {
            load()
        }

        fun load() {
            state.value = SeriesEpisodesUiState.Loading
            requested.clear()
            viewModelScope.launch {
                attempt { repository.series(tmdbId).seasons }.fold(
                    onSuccess = { seasons ->
                        val ordered = seasons.sortedBy { if (it.number == SPECIALS) Int.MAX_VALUE else it.number }
                        state.value = SeriesEpisodesUiState.Ready(ordered.map { SeasonSection(it, SeasonEpisodesState.Loading) })
                        loadSeason(seasonNumber)
                    },
                    onFailure = { state.value = SeriesEpisodesUiState.Error(it.toSeerrError()) },
                )
            }
        }

        /** Reads a season's episodes the first time the page shows it or comes near it; later calls do nothing. */
        fun onSeasonNear(number: Int) = loadSeason(number)

        fun retrySeason(number: Int) {
            requested.remove(number)
            loadSeason(number)
        }

        private fun loadSeason(number: Int) {
            if (state.value !is SeriesEpisodesUiState.Ready || !requested.add(number)) return
            setEpisodes(number, SeasonEpisodesState.Loading)
            viewModelScope.launch {
                setEpisodes(
                    number,
                    attempt { repository.season(tmdbId, number) }.fold(
                        onSuccess = { SeasonEpisodesState.Loaded(it) },
                        onFailure = { SeasonEpisodesState.Failed(it.toSeerrError()) },
                    ),
                )
            }
        }

        private fun setEpisodes(
            number: Int,
            episodes: SeasonEpisodesState,
        ) = state.update { current ->
            (current as? SeriesEpisodesUiState.Ready)?.let { ready ->
                ready.copy(seasons = ready.seasons.map { if (it.season.number == number) it.copy(episodes = episodes) else it })
            } ?: current
        }

        private companion object {
            const val SPECIALS = 0
        }
    }
