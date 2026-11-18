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

/**
 * [PersonRepository] over the Seerr API. [serverUrl] is the server's address with its trailing slash, for the person's page on it.
 * [io] is the dispatcher the calls run on; it has no default.
 */
class SeerrPersonRepository(
    private val api: suspend () -> SeerrApi,
    private val serverUrl: suspend () -> String,
    private val io: CoroutineDispatcher,
) : PersonRepository {
    override suspend fun details(personId: Int): PersonDetails =
        withContext(io) { api().personDetails(personId).toPersonDetails(webUrl = serverUrl() + "person/" + personId) }

    override suspend fun credits(personId: Int): PersonCredits = withContext(io) { api().personCredits(personId).toPersonCredits() }
}

internal fun SeerrPersonDetailsDto.toPersonDetails(webUrl: String) =
    PersonDetails(
        personId = id,
        name = name,
        knownFor = knownForDepartment?.takeIf { it.isNotBlank() },
        biography = biography?.takeIf { it.isNotBlank() },
        birthday = birthday?.takeIf { it.isNotBlank() },
        deathday = deathday?.takeIf { it.isNotBlank() },
        placeOfBirth = placeOfBirth?.takeIf { it.isNotBlank() },
        alsoKnownAs = alsoKnownAs.filter { it.isNotBlank() },
        gender = gender.toPersonGender(),
        popularity = popularity?.takeIf { it > 0.0 },
        imdbId = imdbId?.takeIf { it.isNotBlank() },
        homepage = homepage?.takeIf { it.isNotBlank() },
        photoUrl = profilePath?.toTmdbPosterUrl(),
        webUrl = webUrl,
    )

private const val TMDB_FEMALE = 1
private const val TMDB_MALE = 2
private const val TMDB_NON_BINARY = 3

private fun Int?.toPersonGender(): PersonGender? =
    when (this) {
        TMDB_FEMALE -> PersonGender.Female
        TMDB_MALE -> PersonGender.Male
        TMDB_NON_BINARY -> PersonGender.NonBinary
        else -> null
    }

internal fun SeerrPersonCreditsDto.toPersonCredits() = PersonCredits(cast.toCredits { it.character }, crew.toCredits { it.job })

/**
 * Each title once, in the order the server listed them, which is TMDB's. TMDB lists a title again for every role, so a list keyed on the
 * title would throw on the repeat: the roles are joined under the first listing.
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
                rating = first.voteAverage?.toFloat()?.takeIf { it > 0f },
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
        }

private fun SeerrPersonCreditDto.kind(): TitleKind? =
    when (mediaType) {
        "movie" -> TitleKind.Movie
        "tv" -> TitleKind.Series
        else -> null
    }
