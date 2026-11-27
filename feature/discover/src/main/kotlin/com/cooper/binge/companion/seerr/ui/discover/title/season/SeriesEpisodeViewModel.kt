package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SeriesEpisodeUiState {
    data object Loading : SeriesEpisodeUiState

    data class Error(
        val error: SeerrError,
    ) : SeriesEpisodeUiState

    /**
     * [standard] and [fourK] are this episode's season as the request sheet lists it in each version; null where the show has no
     * such season. [requestSheetFourK] is set when the sheet was opened from the 4K request, so it starts on that version.
     */
    data class Ready(
        val series: SeriesDetails,
        val episode: SeasonEpisode,
        val seasonNumber: Int,
        val standard: SeasonChoice?,
        val fourK: SeasonChoice?,
        val requestSheetOpen: Boolean = false,
        val requestSheetFourK: Boolean = false,
    ) : SeriesEpisodeUiState {
        /** Only a season can be asked for, and only where the server takes part of a show and the account may request it. */
        private val mayRequestSeason: Boolean get() = series.canRequest && series.partialRequests

        /** This season is still open in the standard version, which the show's own page would also offer. */
        val standardOpen: Boolean get() = mayRequestSeason && series.standard.mayRequest && standard?.isOpen == true

        /** This season is still open in 4K, where the show has a 4K version to request from. */
        val fourKOpen: Boolean get() = mayRequestSeason && series.fourK?.mayRequest == true && fourK?.isOpen == true

        /** The request action shows while either version still has this season open. */
        val canRequest: Boolean get() = standardOpen || fourKOpen
    }
}

sealed interface SeriesEpisodeEvent {
    /** A request went through, for the screen's snackbar. */
    data class Requested(
        val result: RequestResult,
    ) : SeriesEpisodeEvent
}

/**
 * One episode of a show, read from the show's details and its season's episodes. The request sheet holds only this season, so a
 * request made here asks for this season and no other. Detail keyed on an id.
 */
@HiltViewModel(assistedFactory = SeriesEpisodeViewModel.Factory::class)
class SeriesEpisodeViewModel
    @AssistedInject
    constructor(
        private val repository: SeriesDetailsRepository,
        @Assisted("tmdbId") private val tmdbId: Int,
        @Assisted("seasonNumber") private val seasonNumber: Int,
        @Assisted("episodeNumber") private val episodeNumber: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("tmdbId") tmdbId: Int,
                @Assisted("seasonNumber") seasonNumber: Int,
                @Assisted("episodeNumber") episodeNumber: Int,
            ): SeriesEpisodeViewModel
        }

        private val state = MutableStateFlow<SeriesEpisodeUiState>(SeriesEpisodeUiState.Loading)
        val uiState: StateFlow<SeriesEpisodeUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<SeriesEpisodeEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<SeriesEpisodeEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = SeriesEpisodeUiState.Loading
            viewModelScope.launch {
                state.value =
                    attempt { read() }.fold(
                        onSuccess = { it },
                        onFailure = { SeriesEpisodeUiState.Error(it.toSeerrError()) },
                    )
            }
        }

        /** Opens the request sheet on this season, starting on 4K when [fourK] is set. */
        fun openRequestSheet(fourK: Boolean) = updateReady { it.copy(requestSheetOpen = true, requestSheetFourK = fourK) }

        /** Closing the sheet reads the show again, since a request moves this season's state. */
        fun closeRequestSheet() {
            updateReady { it.copy(requestSheetOpen = false, requestSheetFourK = false) }
            reload()
        }

        /** The server answered a request: the sheet closes and the page reads the episode again. */
        fun requested(result: RequestResult) {
            eventFlow.tryEmit(SeriesEpisodeEvent.Requested(result))
            closeRequestSheet()
        }

        /** Reads the episode again where the page stands; a failure keeps what it shows. */
        private fun reload() {
            viewModelScope.launch {
                attempt { read() }.onSuccess { state.value = it }
            }
        }

        private suspend fun read(): SeriesEpisodeUiState.Ready {
            val series = repository.series(tmdbId)
            val episode =
                repository.season(tmdbId, seasonNumber).firstOrNull { it.number == episodeNumber }
                    ?: throw NoSuchElementException("episode $seasonNumber x $episodeNumber")
            val choices = series.seasonChoices
            return SeriesEpisodeUiState.Ready(
                series = series,
                episode = episode,
                seasonNumber = seasonNumber,
                standard = choices.standard.firstOrNull { it.number == seasonNumber },
                fourK = choices.fourK.firstOrNull { it.number == seasonNumber },
            )
        }

        private fun updateReady(transform: (SeriesEpisodeUiState.Ready) -> SeriesEpisodeUiState.Ready) =
            state.update { current -> (current as? SeriesEpisodeUiState.Ready)?.let(transform) ?: current }
    }
