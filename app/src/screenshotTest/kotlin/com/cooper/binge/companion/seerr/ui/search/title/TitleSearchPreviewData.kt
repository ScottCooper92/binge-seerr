package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.discover.StudioResult
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
    rating: Double? = null,
    genreIds: List<Int> = emptyList(),
) = TitleResult(kind, id, name, year, null, null, null, rating, availability, null, genreIds)

private const val LONG_NAME = "The Extraordinarily Long and Winding Chronicle of a Very Patient Lighthouse Keeper and Her Companions"

/** A set that covers every status and kind, with one name long enough to wrap at the largest font scale. */
internal fun searchResults(): List<TitleResult> =
    listOf(
        result(1, TitleKind.Movie, "Big Buck Bunny", "2008", TitleAvailability.Available, 7.9, listOf(ACTION, COMEDY)),
        result(2, TitleKind.Series, LONG_NAME, "2021", TitleAvailability.PartiallyAvailable, 8.4, listOf(DRAMA, MYSTERY)),
        result(3, TitleKind.Movie, "Sintel", "2010", TitleAvailability.Pending, 7.4, listOf(ACTION)),
        result(4, TitleKind.Series, "Severance", null, TitleAvailability.Processing),
        result(5, TitleKind.Movie, "Metropolis", "1927", TitleAvailability.Blocklisted, 8.1, listOf(DRAMA)),
        result(6, TitleKind.Person, "Marlowe Quinn", null, TitleAvailability.None),
        result(7, TitleKind.Movie, "Tears of Steel", "2012", TitleAvailability.None, 6.9, listOf(ACTION, DRAMA, COMEDY)),
    )

private const val ACTION = 28
private const val COMEDY = 35
private const val DRAMA = 18
private const val MYSTERY = 9648

private val GENRE_NAMES = mapOf(ACTION to "Action", COMEDY to "Comedy", DRAMA to "Drama", MYSTERY to "Mystery")

/** Few enough rows that the footer is on screen. */
private const val FEW = 2

private val RECENTS = listOf("big buck", "severance", "a search long enough that it has to be cut short at the end of its row on a phone")

private fun trendingRails() =
    Trending.Ready(
        searchResults().filter { it.kind == TitleKind.Movie },
        searchResults().filter {
            it.kind ==
                TitleKind.Series
        },
    )

internal fun idleWithRecentsState() = TitleSearchUiState.Idle(text = "", recents = RECENTS, trending = trendingRails())

internal fun idleWithoutRecentsState() = TitleSearchUiState.Idle(text = "", recents = emptyList(), trending = Trending.Unavailable)

internal fun idleLoadingState() = TitleSearchUiState.Idle(text = "", recents = RECENTS, trending = Trending.Loading)

private fun ready(
    results: List<TitleResult>,
    type: SearchType = SearchType.All,
    appending: Boolean = false,
    appendFailed: Boolean = false,
    studios: List<StudioResult> = STUDIOS,
) = TitleSearchUiState.Ready(
    text = "lighthouse",
    results = results.filter(type::keeps),
    studios = studios,
    loaded = results.size,
    type = type,
    counts = results.typeCounts(studios.size),
    genreNames = GENRE_NAMES,
    hasMore = true,
    appending = appending,
    appendFailed = appendFailed,
)

internal fun readyResultsState() = ready(searchResults())

private val STUDIOS =
    listOf(
        StudioResult(1, "Lighthouse Pictures", null),
        StudioResult(2, "Beacon Films and a Very Long Studio Name That Wraps", null),
    )

internal fun readyStudiosState() = ready(searchResults(), SearchType.Studios)

internal fun readyMoviesState() = ready(searchResults(), SearchType.Movies)

/** A group the pages so far hold none of, with no more pages to look in. */
internal fun readyEmptyTypeState() = ready(searchResults().filter { it.kind != TitleKind.Person }, SearchType.People).copy(hasMore = false)

/** The same group while more pages remain: a spinner, as the next page is on its way. */
internal fun readyEmptyTypeLoadingState() = ready(searchResults().filter { it.kind != TitleKind.Person }, SearchType.People)

internal fun readyAppendingState() = ready(searchResults().take(FEW), appending = true)

internal fun readyAppendFailedState() = ready(searchResults().take(FEW), appendFailed = true)

internal fun noResultsState() = TitleSearchUiState.NoResults("qzxv", "qzxv", SearchType.All)

internal fun failedState() = TitleSearchUiState.Error("lighthouse", SeerrError.Unreachable)

internal val noActions =
    TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})
