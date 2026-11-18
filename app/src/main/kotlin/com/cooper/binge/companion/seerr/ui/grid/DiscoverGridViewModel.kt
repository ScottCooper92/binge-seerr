package com.cooper.binge.companion.seerr.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.core.grid.PagedList
import com.cooper.binge.companion.seerr.core.grid.PagedListState
import com.cooper.binge.companion.seerr.core.grid.SortFilterQuery
import com.cooper.binge.companion.seerr.core.grid.SortFilterState
import com.cooper.binge.companion.seerr.core.search.SearchPage
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverGridRepository
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.grid.GridRequest
import com.cooper.binge.companion.seerr.discover.grid.keeps
import com.cooper.binge.companion.seerr.discover.grid.without
import com.cooper.binge.companion.seerr.discover.grid.startingFilters
import com.cooper.binge.companion.seerr.discover.grid.startingSort
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.seerr.toSeerrError
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
        private val media: HubMediaSource,
        @Assisted private val kind: TitleKind,
        @Assisted private val preset: GridPreset,
        @Assisted("title") private val title: String?,
        @Assisted("upcomingFrom") upcomingFrom: String,
    ) : ViewModel() {
        private val starting = preset.startingFilters(upcomingFrom)
        private val sortFilter = SortFilterState(starting, preset.startingSort(kind))
        private val refinable = MutableStateFlow<Boolean?>(null)
        private val eventFlow = MutableSharedFlow<DiscoverGridEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<DiscoverGridEvent> = eventFlow.asSharedFlow()

        private val list =
            PagedList(viewModelScope, sortFilter.query, ::fetch, keyOf = { it.kind to it.tmdbId })

        val uiState: StateFlow<DiscoverGridUiState> =
            combine(
                list.state,
                sortFilter.query,
                refinable,
            ) { page, query, canRefine -> toUiState(page, controls(query, canRefine == true)) }
                .stateIn(viewModelScope, SharingStarted.Lazily, DiscoverGridUiState.Loading(controls(sortFilter.query.value, false)))

        init {
            viewModelScope.launch { refinable.value = attempt { grid.canRefine(preset) }.getOrDefault(false) }
        }

        private suspend fun fetch(
            query: SortFilterQuery<DiscoverFilters, DiscoverSort>,
            page: Int,
        ): SearchPage<HubCard> {
            val loaded = grid.load(GridRequest(kind, preset, query.filters, query.sort), page)
            val serverUrl = media.serverUrl()
            val cards = loaded.results.filter { it.kind == kind }.map { it.toCard(serverUrl) }
            return SearchPage(cards, loaded.page, loaded.totalPages)
        }

        private fun controls(
            query: SortFilterQuery<DiscoverFilters, DiscoverSort>,
            canRefine: Boolean,
        ): GridControls =
            GridControls(
                sort = query.sort.takeIf { canRefine },
                sortChoices = if (canRefine) DiscoverSort.forKind(kind) else emptyList(),
                filters = query.filters,
                chip = if (canRefine && !starting.isEmpty && query.filters.keeps(starting)) preset.toChip(title) else null,
            )

        /** Applies the order and the filters the sheet edited as one change, so the list starts again once. */
        fun onApply(
            sort: DiscoverSort,
            filters: DiscoverFilters,
        ) = sortFilter.set(filters, sort)

        /** Drops the filter the row started with and keeps any the user added since. */
        fun onClearFilter() = sortFilter.updateFilters { it.without(starting) }

        fun onRetry() = list.retry()

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
            ): DiscoverGridViewModel
        }
    }

internal fun toUiState(
    page: PagedListState<HubCard>,
    controls: GridControls,
): DiscoverGridUiState =
    when (page) {
        PagedListState.Loading -> DiscoverGridUiState.Loading(controls)
        PagedListState.Empty -> DiscoverGridUiState.Empty(controls)
        is PagedListState.Failed -> DiscoverGridUiState.Error(controls, page.cause.toSeerrError())
        is PagedListState.Ready ->
            DiscoverGridUiState.Ready(
                controls,
                page.items,
                page.hasMore,
                page.appending,
                page.appendFailed,
            )
    }

private fun GridPreset.toChip(title: String?): ActiveFilterChip? =
    when (this) {
        GridPreset.Popular, GridPreset.Trending, GridPreset.Watchlist -> null
        GridPreset.Upcoming -> ActiveFilterChip(ChipKind.Upcoming, null)
        is GridPreset.Genre -> ActiveFilterChip(ChipKind.Genre, title)
        is GridPreset.Company -> ActiveFilterChip(ChipKind.Company, title)
        is GridPreset.Language -> ActiveFilterChip(ChipKind.Language, title)
        is GridPreset.Keyword -> ActiveFilterChip(ChipKind.Keyword, title)
        is GridPreset.Provider -> ActiveFilterChip(ChipKind.Provider, title)
    }
