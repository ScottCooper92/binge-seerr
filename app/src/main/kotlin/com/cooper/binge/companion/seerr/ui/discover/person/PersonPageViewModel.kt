package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.discover.person.PersonRepository
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The credits as the page holds them. They load apart from the profile, so a failure leaves the profile on screen. */
sealed interface PersonCreditsState {
    data object Loading : PersonCreditsState

    data class Ready(
        val credits: PersonCredits,
    ) : PersonCreditsState

    data class Error(
        val error: SeerrError,
    ) : PersonCreditsState
}

/** [refreshing] shows the pull's spinner while the page, kept as it is, is read again. */
sealed interface PersonPageUiState {
    data object Loading : PersonPageUiState

    data class Error(
        val error: SeerrError,
    ) : PersonPageUiState

    data class Ready(
        val person: PersonDetails,
        val credits: PersonCreditsState = PersonCreditsState.Loading,
        val refreshing: Boolean = false,
    ) : PersonPageUiState
}

sealed interface PersonPageEvent {
    /** A pull to refresh failed: the page stays as it was, and the screen says so. */
    data object RefreshFailed : PersonPageEvent
}

/** The person page. Detail keyed on an id, so it shares [Lazily][kotlinx.coroutines.flow.SharingStarted.Lazily] with the other detail pages: it loads once. */
@HiltViewModel(assistedFactory = PersonPageViewModel.Factory::class)
class PersonPageViewModel
    @AssistedInject
    constructor(
        private val repository: PersonRepository,
        @Assisted private val personId: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(personId: Int): PersonPageViewModel
        }

        private val state = MutableStateFlow<PersonPageUiState>(PersonPageUiState.Loading)
        val uiState: StateFlow<PersonPageUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<PersonPageEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<PersonPageEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = PersonPageUiState.Loading
            viewModelScope.launch {
                val (details, credits) = read()
                state.value =
                    details.fold(
                        onSuccess = { PersonPageUiState.Ready(it, credits.toState()) },
                        onFailure = { PersonPageUiState.Error(it.toSeerrError()) },
                    )
            }
        }

        /** Reads both again under the pull's spinner, keeping the page as it is; a failed read keeps it and says so. */
        fun refresh() {
            if ((state.value as? PersonPageUiState.Ready)?.refreshing != false) return
            updateReady { it.copy(refreshing = true) }
            viewModelScope.launch {
                val (details, credits) = read()
                details.fold(
                    onSuccess = { person ->
                        val keptCredits =
                            credits.isFailure && (state.value as? PersonPageUiState.Ready)?.credits is PersonCreditsState.Ready
                        updateReady {
                            it.copy(
                                person = person,
                                credits = if (keptCredits) it.credits else credits.toState(),
                                refreshing = false,
                            )
                        }
                        if (keptCredits) eventFlow.tryEmit(PersonPageEvent.RefreshFailed)
                    },
                    onFailure = {
                        updateReady { it.copy(refreshing = false) }
                        eventFlow.tryEmit(PersonPageEvent.RefreshFailed)
                    },
                )
            }
        }

        /** Reads the credits again after they failed; the profile stays. */
        fun retryCredits() {
            updateReady { it.copy(credits = PersonCreditsState.Loading) }
            viewModelScope.launch {
                val credits = attempt { repository.credits(personId) }
                updateReady { it.copy(credits = credits.toState()) }
            }
        }

        private suspend fun read(): Pair<Result<PersonDetails>, Result<PersonCredits>> =
            coroutineScope {
                val credits = async { attempt { repository.credits(personId) } }
                attempt { repository.details(personId) } to credits.await()
            }

        private fun Result<PersonCredits>.toState(): PersonCreditsState =
            fold(
                onSuccess = { PersonCreditsState.Ready(it) },
                onFailure = { PersonCreditsState.Error(it.toSeerrError()) },
            )

        private fun updateReady(transform: (PersonPageUiState.Ready) -> PersonPageUiState.Ready) =
            state.update { (it as? PersonPageUiState.Ready)?.let(transform) ?: it }
    }
