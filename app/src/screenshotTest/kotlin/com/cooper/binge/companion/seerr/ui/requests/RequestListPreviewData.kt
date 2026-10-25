package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS

/**
 * Kept far before [FIXED_NOW_MILLIS], well past the 30-day relative-date window `formatRelativeOrAbsolute` switches on, so
 * the row's requester line always renders the same absolute date, whenever the suite runs.
 */
private const val REQUESTED_AT_MILLIS = 1_759_000_000_000L
private const val MOVIE_TMDB_ID = 545_611
private const val TV_TMDB_ID = 95_396

/** A movie whose media is partially available — a long chip label — with no 4K or seasons. */
internal fun longStatusRow(): RequestItem =
    RequestItem(
        id = 1,
        tmdbId = MOVIE_TMDB_ID,
        mediaType = RequestMediaType.Movie,
        title = "Everything Everywhere All at Once",
        posterUrl = null,
        year = "2022",
        requestedBy = "Priya",
        requestedById = 4,
        requestedAtMillis = REQUESTED_AT_MILLIS,
        status = SeerrRequestStatusCode.Approved,
        mediaStatus = SeerrMediaStatusCode.PartiallyAvailable,
        download = null,
        seasonNumbers = emptyList(),
        is4k = false,
    )

/** The same long label, now sharing the media-type/year line with a 4K badge — the #346 regression risk. */
internal fun fourKLongStatusRow(): RequestItem =
    longStatusRow().copy(id = 2, title = "Dune: Part Two", requestedBy = "Marcus", requestedById = 5, is4k = true)

/** Approved but processing with nothing in the queue (#1719): the longest chip label, "Approved · not downloading". */
internal fun notDownloadingRow(): RequestItem =
    longStatusRow().copy(id = 8, title = "Oppenheimer", year = "2023", mediaStatus = SeerrMediaStatusCode.Processing)

/** A TV request with specific seasons, exercising the optional season-list line. */
internal fun seasonListRow(): RequestItem =
    RequestItem(
        id = 3,
        tmdbId = TV_TMDB_ID,
        mediaType = RequestMediaType.Tv,
        title = "Severance",
        posterUrl = null,
        year = "2022",
        requestedBy = "Grace",
        requestedById = 6,
        requestedAtMillis = REQUESTED_AT_MILLIS,
        status = SeerrRequestStatusCode.Pending,
        mediaStatus = SeerrMediaStatusCode.Pending,
        download = null,
        seasonNumbers = listOf(1, 2, 3),
        is4k = false,
    )

/** A TV request with a title long enough to wrap, a long status, a year and one season: the row at its tallest. */
internal fun longTitleTvRow(): RequestItem =
    seasonListRow().copy(
        id = 7,
        title = "The Lord of the Rings: The Rings of Power",
        requestedBy = "Scott",
        requestedById = 1,
        status = SeerrRequestStatusCode.Approved,
        mediaStatus = SeerrMediaStatusCode.PartiallyAvailable,
        seasonNumbers = listOf(1),
    )
