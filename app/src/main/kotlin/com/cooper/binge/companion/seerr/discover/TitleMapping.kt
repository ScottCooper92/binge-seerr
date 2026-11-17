package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrTitlePageDto
import com.cooper.binge.companion.seerr.seerr.SeerrTitleResultDto

private const val YEAR_LENGTH = 4

/** The page with its rows mapped; a row this app cannot show (a media type it does not know, or no id) is left out. */
internal fun SeerrTitlePageDto.toTitlePage(defaultKind: TitleKind?): TitlePage =
    TitlePage(
        results = results.mapNotNull { it.toTitleResult(defaultKind) },
        page = page,
        totalPages = totalPages,
    )

internal fun SeerrTitleResultDto.toTitleResult(defaultKind: TitleKind?): TitleResult? {
    val kind = mediaType?.let(::kindOf) ?: defaultKind ?: return null
    val tmdbId = tmdbId ?: id ?: return null
    val name = (if (kind == TitleKind.Movie) title else name) ?: title ?: name ?: return null
    val date = releaseDate ?: firstAirDate
    return TitleResult(
        kind = kind,
        tmdbId = tmdbId,
        name = name,
        year = date?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH && it.all(Char::isDigit) },
        artworkPath = if (kind == TitleKind.Person) profilePath else posterPath,
        backdropPath = backdropPath,
        overview = overview?.takeIf { it.isNotBlank() },
        rating = voteAverage?.takeIf { it > 0.0 },
        availability = mediaInfo?.status.toAvailability(),
        mediaId = mediaInfo?.id,
    )
}

private fun kindOf(mediaType: String): TitleKind? =
    when (mediaType) {
        "movie" -> TitleKind.Movie
        "tv" -> TitleKind.Series
        "person" -> TitleKind.Person
        "collection" -> TitleKind.Collection
        else -> null
    }

/** The server's own status, with every state this app does not show (unknown, deleted, none) as [TitleAvailability.None]. */
internal fun SeerrMediaStatusCode?.toAvailability(): TitleAvailability =
    when (this) {
        SeerrMediaStatusCode.Pending -> TitleAvailability.Pending
        SeerrMediaStatusCode.Processing -> TitleAvailability.Processing
        SeerrMediaStatusCode.PartiallyAvailable -> TitleAvailability.PartiallyAvailable
        SeerrMediaStatusCode.Available -> TitleAvailability.Available
        SeerrMediaStatusCode.Blocklisted -> TitleAvailability.Blocklisted
        else -> TitleAvailability.None
    }
