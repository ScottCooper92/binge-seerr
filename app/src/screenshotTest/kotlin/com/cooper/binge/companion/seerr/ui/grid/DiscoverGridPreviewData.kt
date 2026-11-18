package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.seerr.SeerrError

private fun card(
    id: Int,
    name: String,
    availability: TitleAvailability,
) = HubCard(TitleKind.Movie, id, name, "2021", null, availability, "https://server.example/movie/$id")

private const val LONG_NAME = "The Extraordinarily Long and Winding Chronicle of a Very Patient Lighthouse Keeper"

/** Every status, and one name long enough to wrap at the largest font scale. */
internal fun gridCards(): List<HubCard> =
    listOf(
        card(1, "Big Buck Bunny", TitleAvailability.Available),
        card(2, LONG_NAME, TitleAvailability.PartiallyAvailable),
        card(3, "Sintel", TitleAvailability.Pending),
        card(4, "Tears of Steel", TitleAvailability.Processing),
        card(5, "Metropolis", TitleAvailability.Blocklisted),
        card(6, "Nosferatu", TitleAvailability.None),
        card(7, "Parasite", TitleAvailability.None),
        card(8, "Spirited Away", TitleAvailability.Available),
    )

private val DRAMA = ActiveFilterChip(ChipKind.Genre, "Drama")

private val SORTED = GridControls(DiscoverSort.PopularityDesc, DiscoverSort.forKind(TitleKind.Movie), DRAMA)

private val UNFILTERED = GridControls(DiscoverSort.RatingDesc, DiscoverSort.forKind(TitleKind.Movie), null)

internal fun readyGridState() = DiscoverGridUiState.Ready(SORTED, gridCards(), hasMore = true, appending = false, appendFailed = false)

internal fun unfilteredGridState() =
    DiscoverGridUiState.Ready(UNFILTERED, gridCards(), hasMore = true, appending = false, appendFailed = false)

internal fun appendingGridState() =
    DiscoverGridUiState.Ready(SORTED, gridCards().take(FEW), hasMore = true, appending = true, appendFailed = false)

internal fun appendFailedGridState() =
    DiscoverGridUiState.Ready(SORTED, gridCards().take(FEW), hasMore = true, appending = false, appendFailed = true)

internal fun emptyGridState() = DiscoverGridUiState.Empty(SORTED)

internal fun failedGridState() = DiscoverGridUiState.Error(SORTED, SeerrError.Unreachable)

internal fun loadingGridState() = DiscoverGridUiState.Loading(SORTED)

internal fun plainGridState() =
    DiscoverGridUiState.Ready(GridControls(null, emptyList(), null), gridCards(), hasMore = false, appending = false, appendFailed = false)

/** Few enough cards that the footer is on screen. */
private const val FEW = 2

internal val noGridActions = DiscoverGridActions({}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> })
