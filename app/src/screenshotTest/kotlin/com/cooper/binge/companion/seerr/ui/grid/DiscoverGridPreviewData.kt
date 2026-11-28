package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.seerr.SeerrError

private fun card(
    id: Int,
    name: String,
    availability: TitleAvailability,
    rating: Float? = null,
) = HubCard(TitleKind.Movie, id, name, "2021", null, availability, "https://server.example/movie/$id", rating)

private const val LONG_NAME = "The Extraordinarily Long and Winding Chronicle of a Very Patient Lighthouse Keeper"

/** Every status, and one name long enough to wrap at the largest font scale. */
internal fun gridCards(): List<HubCard> =
    listOf(
        card(1, "Big Buck Bunny", TitleAvailability.Available, 7.9f),
        card(2, LONG_NAME, TitleAvailability.PartiallyAvailable, 8.4f),
        card(3, "Sintel", TitleAvailability.Pending, 7.4f),
        card(4, "Tears of Steel", TitleAvailability.Processing),
        card(5, "Metropolis", TitleAvailability.Blocklisted, 8.1f),
        card(6, "Nosferatu", TitleAvailability.None),
        card(7, "Parasite", TitleAvailability.None, 8.5f),
        card(8, "Spirited Away", TitleAvailability.Available),
    )

private val DRAMA = ActiveFilterChip(ChipKind.Genre, "Drama")

private val SORTED = GridControls(DiscoverSort.PopularityDesc, DRAMA)

private val ADDED_FILTERS =
    DiscoverFilters(
        language = "ja",
        providers = setOf(8, 337),
        region = "GB",
        certificationCountry = "GB",
        certificationLte = "15",
        keywords = setOf(9715),
        status = setOf(0, 3),
    )

private val ADDED =
    GridControls(
        sort = DiscoverSort.PopularityDesc,
        chip = DRAMA,
        filters = ADDED_FILTERS,
        added = ADDED_FILTERS,
        vocabulary =
            FilterVocabulary(
                languages = listOf(FilterOption("ja", "Japanese")),
                defaultRegion = "GB",
                providers = mapOf("GB" to listOf(FilterOption("8", "Netflix"), FilterOption("337", "Disney Plus"))),
                keywordNames = mapOf(9715 to "superhero"),
            ),
        groups = listOf(FilterGroup.Language, FilterGroup.Where, FilterGroup.Certification, FilterGroup.Keywords, FilterGroup.Status),
    )

private val UNFILTERED = GridControls(DiscoverSort.RatingDesc, null)

internal fun readyGridState() = DiscoverGridUiState.Ready(SORTED, gridCards(), hasMore = true, appending = false, appendFailed = false)

internal fun addedFiltersGridState() =
    DiscoverGridUiState.Ready(ADDED, gridCards(), hasMore = true, appending = false, appendFailed = false)

internal fun unfilteredGridState() =
    DiscoverGridUiState.Ready(UNFILTERED, gridCards(), hasMore = true, appending = false, appendFailed = false)

internal fun appendingGridState() =
    DiscoverGridUiState.Ready(SORTED, gridCards().take(FEW), hasMore = true, appending = true, appendFailed = false)

internal fun appendFailedGridState() =
    DiscoverGridUiState.Ready(SORTED, gridCards().take(FEW), hasMore = true, appending = false, appendFailed = true)

internal fun emptyGridState() = DiscoverGridUiState.Empty(ADDED, GridEmptyReason.Filtered)

internal fun emptyWatchlistState() = DiscoverGridUiState.Empty(GridControls(null, null), GridEmptyReason.Watchlist)

internal fun emptyListState() = DiscoverGridUiState.Empty(GridControls(null, null))

internal fun failedGridState() = DiscoverGridUiState.Error(SORTED, SeerrError.Unreachable)

internal fun loadingGridState() = DiscoverGridUiState.Loading(SORTED)

internal fun plainGridState() =
    DiscoverGridUiState.Ready(GridControls(null, null), gridCards(), hasMore = false, appending = false, appendFailed = false)

/** Few enough cards that the footer is on screen. */
private const val FEW = 2

internal val noGridActions = DiscoverGridActions({}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, FilterLoads({}, {}, {}))
