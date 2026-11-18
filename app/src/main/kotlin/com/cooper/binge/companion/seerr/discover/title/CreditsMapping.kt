package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.seerr.SeerrCastDto
import com.cooper.binge.companion.seerr.seerr.SeerrCrewDto
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl

/**
 * The cast in billing order, one entry per person. TMDB lists a person who plays two characters twice, and a list keyed by
 * person id throws on the repeat, so the characters are joined under the first billing.
 */
internal fun List<SeerrCastDto>.toTitleCast(): List<TitlePerson> =
    sortedBy { it.order ?: Int.MAX_VALUE }
        .groupBy { it.id }
        .values
        .map { roles ->
            val first = roles.first()
            val characters = roles.mapNotNull { it.character?.takeIf(String::isNotBlank) }.distinct()
            TitlePerson(first.id, first.name, characters.joinToString(", ").ifEmpty { null }, first.profilePath?.toTmdbPosterUrl())
        }

/** One entry per person, with every job they held in [this] joined, in the order the server listed them. */
internal fun List<SeerrCrewDto>.toTitleCrew(): List<TitlePerson> =
    groupBy { it.id }
        .values
        .map { jobs ->
            val first = jobs.first()
            val role = jobs.mapNotNull { it.job?.takeIf(String::isNotBlank) }.distinct().joinToString(", ")
            TitlePerson(first.id, first.name, role.ifEmpty { null }, first.profilePath?.toTmdbPosterUrl())
        }
