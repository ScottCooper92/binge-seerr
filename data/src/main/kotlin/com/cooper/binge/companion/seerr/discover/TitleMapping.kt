package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.data.TitleStatusStore
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
        year = date.toReleaseYear(),
        artworkPath = if (kind == TitleKind.Person) profilePath else posterPath,
        backdropPath = backdropPath,
        overview = overview?.takeIf { it.isNotBlank() },
        rating = voteAverage?.takeIf { it > 0.0 },
        availability = cardAvailability(mediaInfo?.status, mediaInfo?.status4k),
        mediaId = mediaInfo?.id,
        genreIds = genreIds,
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
fun SeerrMediaStatusCode?.toAvailability(): TitleAvailability =
    when (this) {
        SeerrMediaStatusCode.Pending -> TitleAvailability.Pending
        SeerrMediaStatusCode.Processing -> TitleAvailability.Processing
        SeerrMediaStatusCode.PartiallyAvailable -> TitleAvailability.PartiallyAvailable
        SeerrMediaStatusCode.Available -> TitleAvailability.Available
        SeerrMediaStatusCode.Blocklisted -> TitleAvailability.Blocklisted
        else -> TitleAvailability.None
    }

/**
 * What a card says of a title with these two versions: the more advanced of the two. [TitleAvailability] is declared in that
 * order (none, requested, processing, partial, available), with blocklisted last, so one version being blocklisted wins as it did.
 */
fun cardAvailability(
    standard: SeerrMediaStatusCode?,
    fourK: SeerrMediaStatusCode?,
): TitleAvailability = maxOf(standard.toAvailability(), fourK.toAvailability())

/** The four-digit year a TMDB date (`1999-03-30`) starts with, or null for a date that does not. */
internal fun String?.toReleaseYear(): String? = this?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH && it.all(Char::isDigit) }

/** The page itself, after [seedInto], for a read that is handed on as it is. */
internal fun SeerrTitlePageDto.seededInto(
    statuses: TitleStatusStore,
    read: Long,
    defaultKind: TitleKind?,
): SeerrTitlePageDto = also { it.seedInto(statuses, read, defaultKind) }

/** Writes what the page's rows say of their titles into [statuses], so every list of cards follows them. */
internal fun SeerrTitlePageDto.seedInto(
    statuses: TitleStatusStore,
    read: Long,
    defaultKind: TitleKind?,
    onWatchlist: Boolean? = null,
) = statuses.seedRows(
    read,
    results.map { Triple(it.mediaType?.let(::kindOf) ?: defaultKind, it.tmdbId ?: it.id, it.mediaInfo) },
    onWatchlist,
)
