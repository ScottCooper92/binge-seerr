package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which credits the page shows: all of them, or only films or only series. */
enum class CreditsFilter(
    val kind: TitleKind?,
) {
    All(null),
    Movies(TitleKind.Movie),
    Series(TitleKind.Series),
}

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

sealed interface PersonPageUiState {
    data object Loading : PersonPageUiState

    data class Error(
        val error: SeerrError,
    ) : PersonPageUiState

    data class Ready(
        val person: PersonDetails,
        val credits: PersonCreditsState = PersonCreditsState.Loading,
        val filter: CreditsFilter = CreditsFilter.All,
    ) : PersonPageUiState {
        /** The credits the chips leave in; null while they are not read. */
        val visible: PersonCredits? get() = (credits as? PersonCreditsState.Ready)?.credits?.only(filter.kind)
    }
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

        init {
            load()
        }

        fun load() {
            state.value = PersonPageUiState.Loading
            viewModelScope.launch {
                coroutineScope {
                    val credits = async { attempt { repository.credits(personId) } }
                    attempt { repository.details(personId) }.fold(
                        onSuccess = { person ->
                            state.value = PersonPageUiState.Ready(person)
                            setCredits(credits.await())
                        },
                        onFailure = {
                            credits.cancel()
                            state.value = PersonPageUiState.Error(it.toSeerrError())
                        },
                    )
                }
            }
        }

        /** Reads the credits again after they failed; the profile stays. */
        fun retryCredits() {
            updateReady { it.copy(credits = PersonCreditsState.Loading) }
            viewModelScope.launch { setCredits(attempt { repository.credits(personId) }) }
        }

        fun setFilter(filter: CreditsFilter) = updateReady { it.copy(filter = filter) }

        private fun setCredits(result: Result<PersonCredits>) {
            val credits =
                result.fold(
                    onSuccess = { PersonCreditsState.Ready(it) },
                    onFailure = { PersonCreditsState.Error(it.toSeerrError()) },
                )
            updateReady { it.copy(credits = credits) }
        }

        private fun updateReady(transform: (PersonPageUiState.Ready) -> PersonPageUiState.Ready) =
            state.update { (it as? PersonPageUiState.Ready)?.let(transform) ?: it }
    }
