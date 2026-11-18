package com.cooper.binge.companion.seerr.discover.person

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import java.time.LocalDate
import java.time.Period

/** What TMDB records of a person's gender, when it records one. */
enum class PersonGender { Female, Male, NonBinary }

/**
 * A person as the page shows them. [birthday] and [deathday] are `yyyy-mm-dd`; [photoUrl] is a full URL; [webUrl] is their page on
 * the server, which Share sends. [imdbId] and [homepage] are what the server gave for the links, unchecked.
 */
data class PersonDetails(
    val personId: Int,
    val name: String,
    val knownFor: String?,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val alsoKnownAs: List<String>,
    val gender: PersonGender?,
    val popularity: Double?,
    val imdbId: String?,
    val homepage: String?,
    val photoUrl: String?,
    val webUrl: String,
) {
    /** How old they were when they died, from the two dates; null where either is missing or does not parse. */
    val ageAtDeath: Int? get() = yearsBetween(birthday, deathday)
}

private fun yearsBetween(
    from: String?,
    to: String?,
): Int? {
    val start = from?.toLocalDateOrNull() ?: return null
    val end = to?.toLocalDateOrNull() ?: return null
    return Period.between(start, end).years.takeIf { it >= 0 }
}

private fun String.toLocalDateOrNull(): LocalDate? = attempt { LocalDate.parse(take(ISO_DATE_LENGTH)) }.getOrNull()

private const val ISO_DATE_LENGTH = 10

/**
 * One title a person is in, once however many roles they had in it. [role] is the characters or jobs joined; [rating] is the
 * community score, null where there is none; [availability] is what the server holds of it.
 */
data class PersonCredit(
    val kind: TitleKind,
    val tmdbId: Int,
    val title: String,
    val rating: Float?,
    val posterUrl: String?,
    val role: String?,
    val availability: TitleAvailability,
) {
    /** One key for a cell: a film and a show can share a TMDB id. */
    val key: String get() = "${kind.name}-$tmdbId"
}

/** Both lists in the order the server gave them, each title once. */
data class PersonCredits(
    val cast: List<PersonCredit>,
    val crew: List<PersonCredit>,
) {
    val isEmpty: Boolean get() = cast.isEmpty() && crew.isEmpty()
}

/** What the person page reads. It knows nothing of Retrofit, so a view model holds this and a test holds a fake. */
interface PersonRepository {
    suspend fun details(personId: Int): PersonDetails

    suspend fun credits(personId: Int): PersonCredits
}
