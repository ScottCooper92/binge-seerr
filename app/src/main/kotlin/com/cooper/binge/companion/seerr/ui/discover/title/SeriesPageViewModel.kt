package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.SeriesDetailsRepository
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
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

/** One season's episodes as the page holds them: read once when the season is first selected, and again only after a failure. */
sealed interface EpisodesState {
    data object Loading : EpisodesState

    data class Ready(
        val episodes: List<SeasonEpisode>,
    ) : EpisodesState

    data class Error(
        val error: SeerrError,
    ) : EpisodesState
}

sealed interface SeriesPageUiState {
    data object Loading : SeriesPageUiState

    data class Error(
        val error: SeerrError,
    ) : SeriesPageUiState

    /**
     * [extras] is null until the sections that load apart have arrived, and a section that failed is simply empty in it.
     * [selectedSeason] is the season whose episodes show, and [episodes] holds what has been read of each season.
     */
    data class Ready(
        val series: SeriesDetails,
        val extras: TitleExtras? = null,
        val requestSheetOpen: Boolean = false,
        /** The request sheet was opened by Request in 4K, so it starts on that version whatever the standard one still allows. */
        val requestSheetFourK: Boolean = false,
        /** The request the primary last opened to manage. Kept after the sheet closes, so a moderation's result still has its sheet to land in. */
        val manageRequestId: Int? = null,
        val manageSheetOpen: Boolean = false,
        val statusSheetOpen: Boolean = false,
        val reportSheetOpen: Boolean = false,
        val selectedSeason: Int? = null,
        val episodes: Map<Int, EpisodesState> = emptyMap(),
    ) : SeriesPageUiState {
        /** The Request button shows while either version still has a season to ask for. */
        val mayRequest: Boolean get() = series.standard.mayRequest || series.fourK?.mayRequest == true
    }
}

sealed interface SeriesPageEvent {
    /** A request went through, for the screen's snackbar. */
    data class Requested(
        val result: RequestResult,
    ) : SeriesPageEvent

    /** The read after a request failed: the page keeps what it had, and the screen offers to try again. */
    data object ReloadFailed : SeriesPageEvent

    /** The title came off the blocklist, for the screen's snackbar; the page reads it again. */
    data object Unblocked : SeriesPageEvent

    /** The server refused or could not be reached, so the title stays blocklisted. */
    data class UnblockFailed(
        val error: SeerrError,
    ) : SeriesPageEvent
}

/** The series page. Detail keyed on an id, so it shares [Lazily][kotlinx.coroutines.flow.SharingStarted.Lazily] with the other detail pages: it loads once. */
@HiltViewModel(assistedFactory = SeriesPageViewModel.Factory::class)
class SeriesPageViewModel
    @AssistedInject
    constructor(
        private val repository: SeriesDetailsRepository,
        @Assisted private val tmdbId: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(tmdbId: Int): SeriesPageViewModel
        }

        private val state = MutableStateFlow<SeriesPageUiState>(SeriesPageUiState.Loading)
        val uiState: StateFlow<SeriesPageUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<SeriesPageEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<SeriesPageEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = SeriesPageUiState.Loading
            viewModelScope.launch {
                attempt { repository.series(tmdbId) }.fold(
                    onSuccess = { series ->
                        val first = (series.seasons.firstOrNull { it.number > 0 } ?: series.seasons.firstOrNull())?.number
                        state.value = SeriesPageUiState.Ready(series, selectedSeason = first)
                        loadExtras()
                        first?.let(::loadEpisodes)
                    },
                    onFailure = { state.value = SeriesPageUiState.Error(it.toSeerrError()) },
                )
            }
        }

        /** Shows a season's episodes, reading them the first time. */
        fun selectSeason(number: Int) {
            val current = ready ?: return
            if (current.series.seasons.none { it.number == number }) return
            updateReady { it.copy(selectedSeason = number) }
            loadEpisodes(number)
        }

        fun retryEpisodes() {
            ready?.selectedSeason?.let(::loadEpisodes)
        }

        /** The primary was tapped: the request sheet or one request's sheet, as the page's state says, or nothing where there is none to open. */
        fun onPrimary() =
            updateReady { ready ->
                when (
                    val tap =
                        titleActionsModel(
                            ready.series.standard,
                            ready.series.fourK,
                            ready.series.requests,
                            ready.series.mayReport,
                        ).primary.tap
                ) {
                    PrimaryTap.RequestSheet -> ready.copy(requestSheetOpen = true)
                    is PrimaryTap.ManageRequest -> ready.copy(manageRequestId = tap.requestId, manageSheetOpen = true)
                    PrimaryTap.StatusSheet -> ready.copy(statusSheetOpen = true)
                }
            }

        /** The request's sheet closed. The id stays, and a change made in it reloads the page through the sheet's own `onChanged`. */
        fun closeManageSheet() = updateReady { it.copy(manageSheetOpen = false) }

        fun closeStatusSheet() = updateReady { it.copy(statusSheetOpen = false) }

        /** Takes the title off the blocklist from the status sheet: the sheet closes, the page reads the title again, and a snackbar says so. */
        fun unblock() {
            val ready = state.value as? SeriesPageUiState.Ready ?: return
            if (!ready.series.mayUnblock) return
            closeStatusSheet()
            viewModelScope.launch {
                attempt { repository.unblock(tmdbId) }.fold(
                    onSuccess = {
                        eventFlow.tryEmit(SeriesPageEvent.Unblocked)
                        reload()
                    },
                    onFailure = { eventFlow.tryEmit(SeriesPageEvent.UnblockFailed(it.toSeerrError())) },
                )
            }
        }

        /** Request in 4K, tapped in a request's sheet: that sheet gives way to the request sheet. */
        fun requestFourKFromManage() = updateReady { it.copy(manageSheetOpen = false, requestSheetOpen = true, requestSheetFourK = true) }

        fun setReportSheetOpen(open: Boolean) = updateReady { it.copy(reportSheetOpen = open) }

        /** Closing the sheet reads the show again, since a request moves its seasons and a refused one may mean the server's state moved. */
        fun closeRequestSheet() {
            updateReady { it.copy(requestSheetOpen = false, requestSheetFourK = false) }
            reload()
        }

        /** Reads the show again where the page stands: a failure keeps what it shows and says so, rather than dropping to the loading page. */
        fun reload() {
            viewModelScope.launch {
                attempt { repository.series(tmdbId) }.fold(
                    onSuccess = { series -> updateReady { it.copy(series = series) } },
                    onFailure = { eventFlow.tryEmit(SeriesPageEvent.ReloadFailed) },
                )
            }
        }

        /** The server answered a request: the sheet closes and the page reads the show again, so its seasons move. */
        fun requested(result: RequestResult) {
            eventFlow.tryEmit(SeriesPageEvent.Requested(result))
            closeRequestSheet()
        }

        private fun loadEpisodes(number: Int) {
            if (ready?.episodes?.get(number) is EpisodesState.Loading || ready?.episodes?.get(number) is EpisodesState.Ready) return
            updateReady { it.copy(episodes = it.episodes + (number to EpisodesState.Loading)) }
            viewModelScope.launch {
                val result =
                    attempt { repository.season(tmdbId, number) }.fold(
                        onSuccess = { EpisodesState.Ready(it) },
                        onFailure = { EpisodesState.Error(it.toSeerrError()) },
                    )
                updateReady { it.copy(episodes = it.episodes + (number to result)) }
            }
        }

        private val ready: SeriesPageUiState.Ready? get() = state.value as? SeriesPageUiState.Ready

        private fun loadExtras() {
            viewModelScope.launch {
                val extras = attempt { repository.extras(tmdbId) }.getOrNull() ?: return@launch
                updateReady { it.copy(extras = extras) }
            }
        }

        private fun updateReady(transform: (SeriesPageUiState.Ready) -> SeriesPageUiState.Ready) =
            state.update { (it as? SeriesPageUiState.Ready)?.let(transform) ?: it }
    }
