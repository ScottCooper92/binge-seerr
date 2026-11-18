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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CollectionPageUiState {
    data object Loading : CollectionPageUiState

    data class Error(
        val error: SeerrError,
    ) : CollectionPageUiState

    data class Ready(
        val collection: CollectionDetails,
    ) : CollectionPageUiState
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

        init {
            load()
        }

        fun load() {
            state.value = CollectionPageUiState.Loading
            viewModelScope.launch {
                attempt { repository.collection(id) }.fold(
                    onSuccess = { state.value = CollectionPageUiState.Ready(it) },
                    onFailure = { state.value = CollectionPageUiState.Error(it.toSeerrError()) },
                )
            }
        }
    }
