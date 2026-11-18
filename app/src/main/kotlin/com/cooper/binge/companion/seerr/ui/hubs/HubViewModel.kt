package com.cooper.binge.companion.seerr.ui.hubs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.discover.sliders.DiscoverSliderRepository
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubCardLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubHeroLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.SliderListState
import com.cooper.binge.companion.seerr.discover.sliders.isCardRow
import com.cooper.binge.companion.seerr.discover.sliders.rowsFor
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.ui.hubs.core.HubLoader
import com.cooper.binge.companion.seerr.ui.hubs.core.HubState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a hub shows. */
sealed interface DiscoverHubUiState {
    data object Loading : DiscoverHubUiState

    /** [pending] rows are still loading and fill in below; [refreshing] is a pull to refresh in flight. */
    data class Ready(
        val rows: List<HubRowUi>,
        val pending: Int,
        val refreshing: Boolean,
        /** The carousel above the rows; empty where the server has no trending list or it failed. */
        val hero: List<HubHero> = emptyList(),
    ) : DiscoverHubUiState

    /** No row has anything to show: every slider is off, or the library is empty. */
    data object Empty : DiscoverHubUiState

    data class Error(
        val error: SeerrError,
    ) : DiscoverHubUiState
}

/** One poster row. [row] says which slider it is, so the screen can title a built-in one in the user's language. */
data class HubRowUi(
    val row: HubRow,
    val cards: List<HubCard>,
)

/**
 * One hub, Movies or TV. The rows are the server's sliders for [tab], read through the shared slider list, and each loads
 * on its own so a slow row does not hold the others back.
 */
@HiltViewModel(assistedFactory = HubViewModel.Factory::class)
class HubViewModel
    @AssistedInject
    constructor(
        private val sliders: DiscoverSliderRepository,
        cards: HubCardLoader,
        private val heroes: HubHeroLoader,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @Assisted private val tab: HubTab,
    ) : ViewModel() {
        private val loader = HubLoader<HubRow, HubCard>(viewModelScope, dispatcher) { cards.load(it) }

        private val hero = MutableStateFlow<List<HubHero>>(emptyList())

        val uiState: StateFlow<DiscoverHubUiState> =
            combine(sliders.sliders, loader.state, loader.refreshing, hero) { list, rows, refreshing, hero ->
                toUiState(list, rows, refreshing, hero)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DiscoverHubUiState.Loading)

        init {
            viewModelScope.launch {
                sliders.sliders.collect { list ->
                    if (list is SliderListState.Ready) loader.show(list.sliders.rowsFor(tab).filter { it.isCardRow() })
                }
            }
            load()
        }

        /** Reads the slider list, the hero and every row again, as a pull to refresh does. */
        fun refresh() {
            load()
            loader.refresh()
        }

        private fun load() {
            viewModelScope.launch { attempt { sliders.refresh() } }
            // A failed hero leaves the last one, or none: it never holds the rows.
            viewModelScope.launch { attempt { heroes.load(tab) }.onSuccess { hero.value = it } }
        }

        @AssistedFactory
        interface Factory {
            fun create(tab: HubTab): HubViewModel
        }
    }

private const val STOP_TIMEOUT_MILLIS = 5_000L

private fun toUiState(
    list: SliderListState,
    rows: HubState<HubRow, HubCard>,
    refreshing: Boolean,
    hero: List<HubHero>,
): DiscoverHubUiState =
    when (list) {
        SliderListState.Loading -> DiscoverHubUiState.Loading
        is SliderListState.Failed -> DiscoverHubUiState.Error(list.error.toSeerrError())
        is SliderListState.Ready ->
            when (rows) {
                HubState.Loading -> DiscoverHubUiState.Loading
                HubState.Empty -> DiscoverHubUiState.Empty
                is HubState.Failed -> DiscoverHubUiState.Error(rows.error.toSeerrError())
                is HubState.Ready -> DiscoverHubUiState.Ready(rows.rows.map { HubRowUi(it.key, it.items) }, rows.pending, refreshing, hero)
            }
    }
