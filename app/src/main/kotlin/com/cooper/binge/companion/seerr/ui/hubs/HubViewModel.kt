package com.cooper.binge.companion.seerr.ui.hubs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.DiscoverSliderRepository
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubCardLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.sliders.HubHeroLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubRow
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.HubTile
import com.cooper.binge.companion.seerr.discover.sliders.HubTileLoader
import com.cooper.binge.companion.seerr.discover.sliders.SliderListState
import com.cooper.binge.companion.seerr.discover.sliders.isTileRow
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a hub shows. */
sealed interface DiscoverHubUiState {
    data object Loading : DiscoverHubUiState

    /** [pending] rows are still loading and fill in below; [refreshing] is a pull to refresh in flight. */
    data class Ready(
        val rows: List<HubRowUi>,
        val pending: Int,
        val refreshing: Boolean,
        /** The carousel above the rows; empty where the server has no trending list, it failed or has not answered yet. */
        val hero: List<HubHero> = emptyList(),
        /** Whether the hero's place is kept above the rows: while the hero loads, and with it; not once the answer is that there is none. */
        val heroPlace: Boolean = true,
        /** Whether the "Browse all" row shows: it promises filters, which the grid only has on a server that takes them. */
        val browseAll: Boolean = true,
    ) : DiscoverHubUiState

    /** No row has anything to show: every slider is off, or the library is empty. */
    data object Empty : DiscoverHubUiState

    data class Error(
        val error: SeerrError,
    ) : DiscoverHubUiState
}

/** One row of a hub. [row] says which slider it is, so the screen can title a built-in one in the user's language. */
sealed interface HubRowUi {
    val row: HubRow

    /** A row of posters. */
    data class Cards(
        override val row: HubRow,
        val cards: List<HubCard>,
    ) : HubRowUi

    /** A row of category tiles: genres, studios or networks. */
    data class Tiles(
        override val row: HubRow,
        val tiles: List<HubTile>,
    ) : HubRowUi
}

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
        tiles: HubTileLoader,
        private val heroes: HubHeroLoader,
        private val grid: DiscoverGridRepository,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @Assisted private val tab: HubTab,
    ) : ViewModel() {
        /** Each row's one "item" is the finished [HubRowUi], so an empty row is the loader's to drop and nothing is wrapped and unwrapped. */
        private val loader =
            HubLoader<HubRow, HubRowUi>(viewModelScope, dispatcher) { row -> listOfNotNull(rowUi(row, cards, tiles)) }

        /** Null until the server says whether it has a trending list; the hub waits for that like it does for [browseAll]. */
        private val hero = MutableStateFlow<HeroSlot?>(null)

        /**
         * Whether the Browse all row shows. Null until the server says whether it can filter, and the hub waits for that, up to
         * [BROWSE_ALL_WAIT_MILLIS] once its rows are ready, so the row is not shown and then taken away from under the user.
         */
        private val browseAll = MutableStateFlow<Boolean?>(null)

        val uiState: StateFlow<DiscoverHubUiState> =
            combine(sliders.sliders, loader.state, loader.refreshing, hero, browseAll) { list, rows, refreshing, hero, browseAll ->
                toUiState(list, rows, refreshing, hero, browseAll)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DiscoverHubUiState.Loading)

        init {
            viewModelScope.launch {
                sliders.sliders.collect { list ->
                    if (list is SliderListState.Ready) loader.show(list.sliders.rowsFor(tab))
                }
            }
            viewModelScope.launch {
                loader.state.first { it is HubState.Ready }
                delay(BROWSE_ALL_WAIT_MILLIS)
                browseAll.update { it ?: true }
                hero.update { it ?: HeroSlot.Loading }
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
            viewModelScope.launch { loadHero() }
            viewModelScope.launch {
                // A failed read keeps the answer the hub already has, and shows Browse all only where it has none.
                val answer = attempt { grid.canRefine(GridPreset.Popular) }.getOrNull()
                browseAll.update { answer ?: it ?: true }
            }
        }

        /** A server with no trending list has no hero place at all; otherwise the place stays until the answer says there is nothing. */
        private suspend fun loadHero() {
            if (hero.value == null) hero.value = if (attempt { heroes.supported() }.getOrDefault(true)) HeroSlot.Loading else HeroSlot.Gone
            // A failed hero leaves the last one, or none: it never holds the rows.
            attempt { heroes.load(tab) }
                .onSuccess { titles ->
                    hero.value = if (titles.isEmpty()) HeroSlot.Gone else HeroSlot.Shown(titles)
                    showScores(titles)
                }.onFailure { hero.update { if (it is HeroSlot.Shown) it else HeroSlot.Gone } }
        }

        /** The review scores join the hero after it shows, and only the hero they were read for: a newer one is left as it is. */
        private suspend fun showScores(titles: List<HubHero>) {
            if (titles.isEmpty()) return
            val scored = heroes.withScores(titles)
            hero.update {
                if (it is HeroSlot.Shown &&
                    it.titles.map(HubHero::tmdbId) == scored.map(HubHero::tmdbId)
                ) {
                    HeroSlot.Shown(scored)
                } else {
                    it
                }
            }
        }

        @AssistedFactory
        interface Factory {
            fun create(tab: HubTab): HubViewModel
        }
    }

/** What the hero's place holds: kept while it loads, filled with the titles, or gone where the answer is that there are none. */
private sealed interface HeroSlot {
    data object Loading : HeroSlot

    data class Shown(
        val titles: List<HubHero>,
    ) : HeroSlot

    data object Gone : HeroSlot
}

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** How long the hub holds its rows for the server's answer on filtering, once they are ready, before it shows Browse all. */
private const val BROWSE_ALL_WAIT_MILLIS = 1_000L

private fun toUiState(
    list: SliderListState,
    rows: HubState<HubRow, HubRowUi>,
    refreshing: Boolean,
    hero: HeroSlot?,
    browseAll: Boolean?,
): DiscoverHubUiState =
    when (list) {
        SliderListState.Loading -> DiscoverHubUiState.Loading
        is SliderListState.Failed -> DiscoverHubUiState.Error(list.error.toSeerrError())
        is SliderListState.Ready ->
            when (rows) {
                HubState.Loading -> DiscoverHubUiState.Loading
                HubState.Empty -> DiscoverHubUiState.Empty
                is HubState.Failed -> DiscoverHubUiState.Error(rows.error.toSeerrError())
                is HubState.Ready ->
                    if (browseAll == null || hero == null) {
                        DiscoverHubUiState.Loading
                    } else {
                        val titles = (hero as? HeroSlot.Shown)?.titles.orEmpty()
                        DiscoverHubUiState.Ready(
                            rows.rows.flatMap { it.items },
                            rows.pending,
                            refreshing,
                            titles,
                            hero != HeroSlot.Gone,
                            browseAll,
                        )
                    }
            }
    }

/** The row as the screen shows it, or null when it has nothing to show. */
private suspend fun rowUi(
    row: HubRow,
    cards: HubCardLoader,
    tiles: HubTileLoader,
): HubRowUi? =
    if (row.isTileRow()) {
        tiles.load(row).takeIf { it.isNotEmpty() }?.let { HubRowUi.Tiles(row, it) }
    } else {
        cards.load(row).takeIf { it.isNotEmpty() }?.let { HubRowUi.Cards(row, it) }
    }
