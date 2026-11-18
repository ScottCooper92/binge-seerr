package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleDetailsRepository
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

sealed interface TitlePageUiState {
    data object Loading : TitlePageUiState

    data class Error(
        val error: SeerrError,
    ) : TitlePageUiState

    /** [extras] is null until the sections that load apart have arrived, and a section that failed is simply empty in it. */
    data class Ready(
        val movie: MovieDetails,
        val extras: TitleExtras? = null,
        val requestSheetOpen: Boolean = false,
        val collectionParts: List<TitleResult> = emptyList(),
    ) : TitlePageUiState {
        /** The Request button shows while either version can still be asked for. */
        val mayRequest: Boolean get() = movie.standard.mayRequest || movie.fourK?.mayRequest == true
    }
}

sealed interface TitlePageEvent {
    /** A request went through, for the screen's snackbar. */
    data class Requested(
        val result: RequestResult,
    ) : TitlePageEvent
}

/** The film page. Detail keyed on an id, so it shares [Lazily][kotlinx.coroutines.flow.SharingStarted.Lazily] with the other detail pages: it loads once. */
@HiltViewModel(assistedFactory = TitlePageViewModel.Factory::class)
class TitlePageViewModel
    @AssistedInject
    constructor(
        private val repository: TitleDetailsRepository,
        @Assisted private val tmdbId: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(tmdbId: Int): TitlePageViewModel
        }

        private val state = MutableStateFlow<TitlePageUiState>(TitlePageUiState.Loading)
        val uiState: StateFlow<TitlePageUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<TitlePageEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<TitlePageEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = TitlePageUiState.Loading
            viewModelScope.launch {
                attempt { repository.movie(tmdbId) }.fold(
                    onSuccess = { movie ->
                        state.value = TitlePageUiState.Ready(movie)
                        movie.collection?.let { loadCollection(it.id) }
                        loadExtras()
                    },
                    onFailure = { state.value = TitlePageUiState.Error(it.toSeerrError()) },
                )
            }
        }

        fun openRequestSheet() = updateReady { if (it.mayRequest) it.copy(requestSheetOpen = true) else it }

        /** Closing the sheet reads the title again, since a refused send may mean the server's state moved under the page. */
        fun closeRequestSheet() {
            updateReady { it.copy(requestSheetOpen = false) }
            viewModelScope.launch {
                attempt { repository.movie(tmdbId) }.onSuccess { movie -> updateReady { it.copy(movie = movie) } }
            }
        }

        /** The server answered a request: the sheet closes and the page reads the title again, so its status moves. */
        fun requested(result: RequestResult) {
            eventFlow.tryEmit(TitlePageEvent.Requested(result))
            closeRequestSheet()
        }

        /** The other films of the film's collection, where it has one; a failed read leaves the rail out, as it does any extra. */
        private fun loadCollection(collectionId: Int) {
            viewModelScope.launch {
                val parts = attempt { repository.collectionParts(collectionId) }.getOrNull() ?: return@launch
                updateReady { it.copy(collectionParts = parts.filterNot { part -> part.tmdbId == tmdbId }) }
            }
        }

        private fun loadExtras() {
            viewModelScope.launch {
                val extras = attempt { repository.extras(TitleKind.Movie, tmdbId) }.getOrNull() ?: return@launch
                updateReady { it.copy(extras = extras) }
            }
        }

        private fun updateReady(transform: (TitlePageUiState.Ready) -> TitlePageUiState.Ready) =
            state.update { (it as? TitlePageUiState.Ready)?.let(transform) ?: it }
    }
