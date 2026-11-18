package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.collection.CollectionRepository
import com.cooper.binge.companion.seerr.discover.request.RequestResult
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

sealed interface CollectionPageUiState {
    data object Loading : CollectionPageUiState

    data class Error(
        val error: SeerrError,
    ) : CollectionPageUiState

    /** [mayRequest] is false until the viewer's rights are read, so the Request button appears only for one who may use it. */
    data class Ready(
        val collection: CollectionDetails,
        val mayRequest: Boolean = false,
        val requestSheetOpen: Boolean = false,
    ) : CollectionPageUiState
}

sealed interface CollectionPageEvent {
    /** A request went through, for the screen's snackbar. */
    data class Requested(
        val result: RequestResult,
    ) : CollectionPageEvent
}

/** The collection page. Detail keyed on an id, so it loads once and `Lazily` is the policy. */
@HiltViewModel(assistedFactory = CollectionPageViewModel.Factory::class)
class CollectionPageViewModel
    @AssistedInject
    constructor(
        private val repository: CollectionRepository,
        @Assisted private val id: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(id: Int): CollectionPageViewModel
        }

        private val state = MutableStateFlow<CollectionPageUiState>(CollectionPageUiState.Loading)
        val uiState: StateFlow<CollectionPageUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<CollectionPageEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<CollectionPageEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = CollectionPageUiState.Loading
            viewModelScope.launch {
                attempt { repository.collection(id) }.fold(
                    onSuccess = {
                        state.value = CollectionPageUiState.Ready(it)
                        loadRights()
                    },
                    onFailure = { state.value = CollectionPageUiState.Error(it.toSeerrError()) },
                )
            }
        }

        /** The sheet opens only for a viewer who may request and while a film is still open to request. */
        fun openRequestSheet() = updateReady { if (it.mayRequest && it.collection.toRequest > 0) it.copy(requestSheetOpen = true) else it }

        /** Closing the sheet reads the collection again, since a refused send may mean the server's state moved under the page. */
        fun closeRequestSheet() {
            updateReady { it.copy(requestSheetOpen = false) }
            viewModelScope.launch {
                attempt { repository.collection(id) }.onSuccess { fresh -> updateReady { it.copy(collection = fresh) } }
            }
        }

        /** The server answered a request: the sheet closes and the page reads the collection again, so its statuses move. */
        fun requested(result: RequestResult) {
            eventFlow.tryEmit(CollectionPageEvent.Requested(result))
            closeRequestSheet()
        }

        private fun loadRights() {
            viewModelScope.launch {
                val may = attempt { repository.mayRequest() }.getOrNull() ?: return@launch
                updateReady { it.copy(mayRequest = may) }
            }
        }

        private fun updateReady(change: (CollectionPageUiState.Ready) -> CollectionPageUiState.Ready) {
            state.update { if (it is CollectionPageUiState.Ready) change(it) else it }
        }
    }
