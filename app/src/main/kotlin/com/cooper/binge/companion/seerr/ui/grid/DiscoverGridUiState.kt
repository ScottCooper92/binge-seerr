package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterCapabilities
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.seerr.SeerrError

/**
 * The filter a preset started the grid with, shown as a chip the user can clear. [label] is the row's own name where the route
 * carried one; the screen words the rest.
 */
data class ActiveFilterChip(
    val kind: ChipKind,
    val label: String?,
)

/** Which preset the chip stands for, for the screen to word when the row had no name. */
enum class ChipKind { Upcoming, Genre, Company, Language, Keyword, Provider }

/** What the bar above the grid offers. [sort] is null where the list takes no order, and then no sort chip is shown. */
data class GridControls(
    val sort: DiscoverSort?,
    val chip: ActiveFilterChip?,
    val filters: DiscoverFilters = DiscoverFilters(),
    val vocabulary: FilterVocabulary = FilterVocabulary(),
    /** The filters the user added to the list's own, one chip each; empty where the list takes no filters. */
    val groups: List<FilterGroup> = emptyList(),
    /** What the chips in [groups] name: the filters in force without the ones the list opened with. */
    val added: DiscoverFilters = DiscoverFilters(),
)

/**
 * The choices the filter sheet's pickers offer; a row whose list is empty, or whose filter the server lacks, is not shown.
 * [providers] are by region code, for the kind the grid opened on. [keywordResults] is null until a keyword has been searched.
 */
data class FilterVocabulary(
    val genres: List<FilterOption> = emptyList(),
    val languages: List<FilterOption> = emptyList(),
    val capabilities: FilterCapabilities = FilterCapabilities(),
    val regions: List<FilterOption> = emptyList(),
    val defaultRegion: String? = null,
    val providers: Map<String, List<FilterOption>> = emptyMap(),
    /** The regions whose providers could not be read; a region in neither map is still loading, and the page asks again when reopened. */
    val providersFailed: Set<String> = emptySet(),
    val certifications: Map<String, List<String>> = emptyMap(),
    val keywordResults: List<FilterOption>? = null,
    val keywordsFailed: Boolean = false,
    val keywordNames: Map<Int, String> = emptyMap(),
)

/** The "see all" grid. The controls stay in every state, so a filter that empties the list can be cleared from it. */
sealed interface DiscoverGridUiState {
    val controls: GridControls

    data class Loading(
        override val controls: GridControls,
    ) : DiscoverGridUiState

    data class Ready(
        override val controls: GridControls,
        val cards: List<HubCard>,
        val hasMore: Boolean,
        val appending: Boolean,
        val appendFailed: Boolean,
    ) : DiscoverGridUiState

    data class Empty(
        override val controls: GridControls,
    ) : DiscoverGridUiState

    data class Error(
        override val controls: GridControls,
        val error: SeerrError,
    ) : DiscoverGridUiState
}

/** What the grid asks its host to do. */
sealed interface DiscoverGridEvent {
    data class Open(
        val card: HubCard,
    ) : DiscoverGridEvent
}
