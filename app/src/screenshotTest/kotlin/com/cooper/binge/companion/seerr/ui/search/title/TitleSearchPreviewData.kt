package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrError

private fun result(
    id: Int,
    kind: TitleKind,
    name: String,
    year: String?,
    availability: TitleAvailability,
    overview: String? = null,
) = TitleResult(kind, id, name, year, null, null, overview, null, availability, null)

private const val LONG_NAME = "The Extraordinarily Long and Winding Chronicle of a Very Patient Lighthouse Keeper and Her Companions"
private const val LONG_OVERVIEW =
    "A lighthouse keeper, a retired cartographer and a stubborn goat spend one winter on an island the maps forget, " +
        "and the letters they write to the mainland say more than they mean to."

/** A set that covers every status and kind, with one name long enough to wrap at the largest font scale. */
internal fun searchResults(): List<TitleResult> =
    listOf(
        result(
            1,
            TitleKind.Movie,
            "Big Buck Bunny",
            "2008",
            TitleAvailability.Available,
            "A giant rabbit takes a quiet revenge on three bullying rodents.",
        ),
        result(2, TitleKind.Series, LONG_NAME, "2021", TitleAvailability.PartiallyAvailable, LONG_OVERVIEW),
        result(3, TitleKind.Movie, "Sintel", "2010", TitleAvailability.Pending),
        result(4, TitleKind.Series, "Severance", null, TitleAvailability.Processing),
        result(5, TitleKind.Movie, "Metropolis", "1927", TitleAvailability.Blocklisted),
        result(6, TitleKind.Person, "Marlowe Quinn", null, TitleAvailability.None),
        result(7, TitleKind.Movie, "Tears of Steel", "2012", TitleAvailability.None),
    )

/** Few enough rows that the footer is on screen. */
private const val FEW = 2

private val RECENTS = listOf("big buck", "severance", "a search long enough that it has to be cut short at the end of its row on a phone")

internal fun idleWithRecentsState() = TitleSearchUiState.Idle(text = "", recents = RECENTS)

internal fun idleWithoutRecentsState() = TitleSearchUiState.Idle(text = "", recents = emptyList())

internal fun readyResultsState() =
    TitleSearchUiState.Ready("lighthouse", searchResults(), hasMore = true, appending = false, appendFailed = false)

internal fun readyAppendingState() =
    TitleSearchUiState.Ready("lighthouse", searchResults().take(FEW), hasMore = true, appending = true, appendFailed = false)

internal fun readyAppendFailedState() =
    TitleSearchUiState.Ready("lighthouse", searchResults().take(FEW), hasMore = true, appending = false, appendFailed = true)

internal fun noResultsState() = TitleSearchUiState.NoResults("qzxv", "qzxv")

internal fun failedState() = TitleSearchUiState.Error("lighthouse", SeerrError.Unreachable)

internal val noActions =
    TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {})
