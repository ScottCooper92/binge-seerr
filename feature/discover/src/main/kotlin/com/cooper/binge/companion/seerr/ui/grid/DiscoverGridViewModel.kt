package com.cooper.binge.companion.seerr.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleStatusMap
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.grid.FilterLookups
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.grid.GridRequest
import com.cooper.binge.companion.seerr.discover.grid.cleared
import com.cooper.binge.companion.seerr.discover.grid.clearedKeeping
import com.cooper.binge.companion.seerr.discover.grid.keeps
import com.cooper.binge.companion.seerr.discover.grid.setGroups
import com.cooper.binge.companion.seerr.discover.grid.startingFilters
import com.cooper.binge.companion.seerr.discover.grid.startingSort
import com.cooper.binge.companion.seerr.discover.grid.without
import com.cooper.binge.companion.seerr.discover.overlaid
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.discover.withoutOffWatchlist
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.homelab.core.presentation.browse.SortFilterQuery
import com.cooper.homelab.core.presentation.browse.SortFilterState
import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.core.presentation.paging.PagedList
import com.cooper.homelab.core.presentation.paging.PagedListState
import com.cooper.homelab.core.presentation.paging.ResultPage
import com.cooper.homelab.core.presentation.paging.numberedPage
import com.cooper.homelab.core.presentation.paging.toGridState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The "see all" grid of one hub row. It starts on the row's own filter and order, which the user can clear or change, and the list
 * starts again from its first page whenever either does. [title] is the row's name, for the chip that stands for its filter.
 * [upcomingFrom] is the first day an upcoming title may come out. A detail keyed on its route, so the state is shared `Lazily`.
 */
@HiltViewModel(assistedFactory = DiscoverGridViewModel.Factory::class)
class DiscoverGridViewModel
    @AssistedInject
    constructor(
        private val grid: DiscoverGridRepository,
        lookups: FilterLookups,
        private val media: HubMediaSource,
        @Assisted private val kind: TitleKind,
        @Assisted private val preset: GridPreset,
        @Assisted("title") private val title: String?,
        @Assisted("upcomingFrom") upcomingFrom: String,
        @Assisted private val initialFilters: DiscoverFilters?,
        @Assisted private val initialSort: DiscoverSort?,
        statuses: TitleStatusStore = TitleStatusStore(),
    ) : ViewModel() {
        private val starting = preset.startingFilters(upcomingFrom)
        private val sortFilter = SortFilterState(initialFilters ?: starting, initialSort ?: preset.startingSort(kind))
        private val loader = FilterVocabularyLoader(viewModelScope, kind, grid, lookups)
        private val refinable = MutableStateFlow<Boolean?>(null)
        private val eventFlow = MutableSharedFlow<DiscoverGridEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<DiscoverGridEvent> = eventFlow.asSharedFlow()

        private val list =
            PagedList(
                scope = viewModelScope,
                query = sortFilter.query,
                first = FIRST_PAGE,
                fetch = ::fetch,
                classify = { it.toServerFailure() },
                keyOf = { it.kind to it.tmdbId },
            )

        val uiState: StateFlow<DiscoverGridUiState> =
            combine(
                list.state.combine(statuses.statuses) { page, held -> page.overlaid(held).onlyWhileOn(preset, held) },
                sortFilter.query,
                refinable,
                loader.vocabulary,
            ) { page, query, canRefine, words ->
                val controls = controls(query, canRefine == true, words)
                toUiState(page, controls, emptyReason(preset, controls))
            }.stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                DiscoverGridUiState.Loading(controls(sortFilter.query.value, false, FilterVocabulary())),
            )

        init {
            viewModelScope.launch {
                val canRefine = attempt { grid.canRefine(preset) }.getOrDefault(false)
                refinable.value = canRefine
                if (canRefine) {
                    loader.load(
                        starting.keywords + starting.excludeKeywords +
                            sortFilter.query.value.filters
                                .let { it.keywords + it.excludeKeywords },
                    )
                }
            }
        }

        private suspend fun fetch(
            query: SortFilterQuery<DiscoverFilters, DiscoverSort>,
            page: Int,
        ): ResultPage<HubCard, Int> {
            val loaded = grid.load(GridRequest(kind, preset, query.filters, query.sort), page)
            val serverUrl = media.serverUrl()
            val cards = loaded.results.filter { preset == GridPreset.OwnWatchlist || it.kind == kind }.map { it.toCard(serverUrl) }
            return numberedPage(cards, loaded.page, loaded.totalPages)
        }

        private fun controls(
            query: SortFilterQuery<DiscoverFilters, DiscoverSort>,
            canRefine: Boolean,
            words: FilterVocabulary,
        ): GridControls =
            GridControls(
                sort = query.sort.takeIf { canRefine },
                filters = query.filters,
                vocabulary = words,
                groups = if (canRefine) query.filters.without(starting).setGroups() else emptyList(),
                added = if (canRefine) query.filters.without(starting) else DiscoverFilters(),
                chip = if (canRefine && !starting.isEmpty && query.filters.keeps(starting)) preset.toChip(title) else null,
            )

        /** Applies the order and the filters the sheet edited as one change, so the list starts again once. */
        fun onApply(
            sort: DiscoverSort,
            filters: DiscoverFilters,
        ) = sortFilter.set(filters, sort)

        /** Drops the filter the row started with and keeps any the user added since. */
        fun onClearFilter() = sortFilter.updateFilters { it.without(starting) }

        /** The sheet's Where to watch page is open on [region]: its streaming providers are loaded once. */
        fun onProvidersNeeded(region: String) = loader.needProviders(region)

        fun onKeywordQuery(query: String) = loader.searchKeywords(query)

        fun onKeywordsNeeded(ids: Set<Int>) = loader.nameKeywords(ids)

        /** Drops one added filter and keeps the rest, and the filter the list opened with while it is still in force. */
        fun onClearGroup(group: FilterGroup) =
            sortFilter.updateFilters { if (it.keeps(starting)) it.clearedKeeping(group, starting) else it.cleared(group) }

        /** Back to the filters the list opened with, dropping the ones the user added. */
        fun onResetFilters() = sortFilter.updateFilters { starting }

        fun onRetry() {
            list.retry()
            loader.retryFailed()
        }

        fun onLoadMore() = list.loadMore()

        fun onOpenTitle(card: HubCard) {
            viewModelScope.launch { eventFlow.emit(DiscoverGridEvent.Open(card)) }
        }

        @AssistedFactory
        interface Factory {
            fun create(
                kind: TitleKind,
                preset: GridPreset,
                @Assisted("title") title: String?,
                @Assisted("upcomingFrom") upcomingFrom: String,
                initialFilters: DiscoverFilters?,
                initialSort: DiscoverSort?,
            ): DiscoverGridViewModel
        }
    }

fun toUiState(
    page: PagedListState<HubCard>,
    controls: GridControls,
    emptyReason: GridEmptyReason = GridEmptyReason.Nothing,
): DiscoverGridUiState =
    when (val grid = page.toGridState(controls)) {
        is GridState.Loading -> DiscoverGridUiState.Loading(controls)
        is GridState.Empty -> DiscoverGridUiState.Empty(controls, emptyReason)
        is GridState.Error -> DiscoverGridUiState.Error(controls, grid.failure.toSeerrError())
        is GridState.Ready -> DiscoverGridUiState.Ready(controls, grid.items, grid.hasMore, grid.appending, grid.appendFailed)
    }

/** A watchlist's own emptiness, or filters the user added matching nothing, or the list being empty by itself. */
private fun emptyReason(
    preset: GridPreset,
    controls: GridControls,
): GridEmptyReason =
    when {
        controls.groups.isNotEmpty() -> GridEmptyReason.Filtered
        preset == GridPreset.Watchlist || preset == GridPreset.OwnWatchlist -> GridEmptyReason.Watchlist
        else -> GridEmptyReason.Nothing
    }

private fun GridPreset.toChip(title: String?): ActiveFilterChip? =
    when (this) {
        GridPreset.Popular, GridPreset.Trending, GridPreset.Watchlist, GridPreset.OwnWatchlist -> null
        GridPreset.Upcoming -> ActiveFilterChip(ChipKind.Upcoming, null)
        is GridPreset.Genre -> ActiveFilterChip(ChipKind.Genre, title)
        is GridPreset.Company -> ActiveFilterChip(ChipKind.Company, title)
        is GridPreset.Language -> ActiveFilterChip(ChipKind.Language, title)
        is GridPreset.Keyword -> ActiveFilterChip(ChipKind.Keyword, title)
        is GridPreset.Provider -> ActiveFilterChip(ChipKind.Provider, title)
    }

/** A watchlist grid loses the titles taken off the list since it was read; every other grid keeps its cards. */
private fun PagedListState<HubCard>.onlyWhileOn(
    preset: GridPreset,
    held: TitleStatusMap,
): PagedListState<HubCard> = if (preset == GridPreset.Watchlist || preset == GridPreset.OwnWatchlist) withoutOffWatchlist(held) else this

/** Seerr counts its pages from one. */
private const val FIRST_PAGE = 1
