package com.cooper.binge.companion.seerr.discover.person

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.toAvailability
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrPersonCreditDto
import com.cooper.binge.companion.seerr.seerr.SeerrPersonCreditsDto
import com.cooper.binge.companion.seerr.seerr.SeerrPersonDetailsDto
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [PersonRepository] over the Seerr API. [io] is the dispatcher the calls run on; it has no default. */
class SeerrPersonRepository(
    private val api: suspend () -> SeerrApi,
    private val io: CoroutineDispatcher,
) : PersonRepository {
    override suspend fun details(personId: Int): PersonDetails = withContext(io) { api().personDetails(personId).toPersonDetails() }

    override suspend fun credits(personId: Int): PersonCredits = withContext(io) { api().personCredits(personId).toPersonCredits() }
}

internal fun SeerrPersonDetailsDto.toPersonDetails() =
    PersonDetails(
        personId = id,
        name = name,
        knownFor = knownForDepartment?.takeIf { it.isNotBlank() },
        biography = biography?.takeIf { it.isNotBlank() },
        birthday = birthday?.takeIf { it.isNotBlank() },
        deathday = deathday?.takeIf { it.isNotBlank() },
        placeOfBirth = placeOfBirth?.takeIf { it.isNotBlank() },
        alsoKnownAs = alsoKnownAs.filter { it.isNotBlank() },
        photoUrl = profilePath?.toTmdbPosterUrl(),
    )

internal fun SeerrPersonCreditsDto.toPersonCredits() = PersonCredits(cast.toCredits { it.character }, crew.toCredits { it.job })

/**
 * Each title once. TMDB lists a title again for every role, so a list keyed on the title would throw on the repeat: the roles are
 * joined under the first listing. Newest first, a title with no date last, then by name so the order does not move between reads.
 */
private fun List<SeerrPersonCreditDto>.toCredits(role: (SeerrPersonCreditDto) -> String?): List<PersonCredit> =
    mapNotNull { credit -> credit.kind()?.let { it to credit } }
        .groupBy { (kind, credit) -> kind to credit.id }
        .values
        .map { listings ->
            val (kind, first) = listings.first()
            PersonCredit(
                kind = kind,
                tmdbId = first.id,
                title = (if (kind == TitleKind.Movie) first.title else first.name).orEmpty(),
                date = (if (kind == TitleKind.Movie) first.releaseDate else first.firstAirDate)?.takeIf { it.isNotBlank() },
                posterUrl = first.posterPath?.toTmdbPosterUrl(),
                role =
                    listings
                        .mapNotNull { (_, it) ->
                            role(it)?.takeIf(String::isNotBlank)
                        }.distinct()
                        .joinToString(", ")
                        .ifEmpty { null },
                availability = first.mediaInfo?.status.toAvailability(),
            )
        }.sortedWith(compareBy<PersonCredit> { it.date == null }.thenByDescending { it.date }.thenBy { it.title })

private fun SeerrPersonCreditDto.kind(): TitleKind? =
    when (mediaType) {
        "movie" -> TitleKind.Movie
        "tv" -> TitleKind.Series
        else -> null
    }
