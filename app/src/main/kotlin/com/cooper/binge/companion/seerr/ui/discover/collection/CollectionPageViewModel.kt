package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.collection.CollectionRepository
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

    /** [mayBlock] is false until the viewer's rights are read, so the menu appears only for one who may use it. */
    data class Ready(
        val collection: CollectionDetails,
        val mayBlock: Boolean = false,
        val confirmingBlock: Boolean = false,
        val changing: Boolean = false,
    ) : CollectionPageUiState
}

sealed interface CollectionPageEvent {
    /** The server blocked or unblocked the collection, for the screen's snackbar. */
    data class Changed(
        val blocked: Boolean,
    ) : CollectionPageEvent

    data class Failed(
        val error: SeerrError,
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

        /** Blocking asks first; unblocking is undone as easily as it is done, so it goes straight through. */
        fun askBlock() = updateReady { if (it.mayBlock && !it.collection.blocked) it.copy(confirmingBlock = true) else it }

        fun dismissBlock() = updateReady { it.copy(confirmingBlock = false) }

        fun setBlocked(blocked: Boolean) {
            val ready = state.value as? CollectionPageUiState.Ready ?: return
            if (!ready.mayBlock || ready.changing) return
            updateReady { it.copy(confirmingBlock = false, changing = true) }
            viewModelScope.launch {
                attempt { repository.setBlocked(id, blocked) }.fold(
                    onSuccess = {
                        attempt { repository.collection(id) }.onSuccess { fresh -> updateReady { it.copy(collection = fresh) } }
                        eventFlow.tryEmit(CollectionPageEvent.Changed(blocked))
                    },
                    onFailure = { eventFlow.tryEmit(CollectionPageEvent.Failed(it.toSeerrError())) },
                )
                updateReady { it.copy(changing = false) }
            }
        }

        private fun loadRights() {
            viewModelScope.launch {
                val may = attempt { repository.mayBlock() }.getOrNull() ?: return@launch
                updateReady { it.copy(mayBlock = may) }
            }
        }

        private fun updateReady(change: (CollectionPageUiState.Ready) -> CollectionPageUiState.Ready) {
            state.update { if (it is CollectionPageUiState.Ready) change(it) else it }
        }
    }
