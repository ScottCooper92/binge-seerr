package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
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
    val sortChoices: List<DiscoverSort>,
    val chip: ActiveFilterChip?,
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
