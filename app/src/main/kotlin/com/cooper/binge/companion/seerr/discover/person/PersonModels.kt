package com.cooper.binge.companion.seerr.discover.person

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind

/** A person as the page shows them. [birthday] and [deathday] are `yyyy-mm-dd`; [photoUrl] is a full URL. */
data class PersonDetails(
    val personId: Int,
    val name: String,
    val knownFor: String?,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val alsoKnownAs: List<String>,
    val photoUrl: String?,
)

/**
 * One title a person is in, once however many roles they had in it. [role] is the characters or jobs joined; [date] is the release
 * or first-air date, null for one with none; [availability] is what the server holds of it.
 */
data class PersonCredit(
    val kind: TitleKind,
    val tmdbId: Int,
    val title: String,
    val date: String?,
    val posterUrl: String?,
    val role: String?,
    val availability: TitleAvailability,
) {
    /** One key for a cell: a film and a show can share a TMDB id. */
    val key: String get() = "${kind.name}-$tmdbId"
}

/** Both lists newest first, undated last, each title once. */
data class PersonCredits(
    val cast: List<PersonCredit>,
    val crew: List<PersonCredit>,
) {
    /** Only the credits of [kind], or all of them for null. */
    fun only(kind: TitleKind?): PersonCredits =
        if (kind == null) this else PersonCredits(cast.filter { it.kind == kind }, crew.filter { it.kind == kind })

    val isEmpty: Boolean get() = cast.isEmpty() && crew.isEmpty()
}

/** What the person page reads. It knows nothing of Retrofit, so a view model holds this and a test holds a fake. */
interface PersonRepository {
    suspend fun details(personId: Int): PersonDetails

    suspend fun credits(personId: Int): PersonCredits
}
