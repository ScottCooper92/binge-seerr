package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile

/**
 * What narrows a discover list, in the app's own terms. Each field is one parameter of the server's `discover/{movies,tv}` route
 * and an unset one is not sent. [releaseFrom] and [releaseTo] are ISO dates (`2024-01-31`), the release date of a movie and the
 * first air date of a show. [company] is a studio for a movie and a network for a show.
 */
data class DiscoverFilters(
    val genres: Set<Int> = emptySet(),
    val company: Int? = null,
    val keywords: Set<Int> = emptySet(),
    val excludeKeywords: Set<Int> = emptySet(),
    val language: String? = null,
    val releaseFrom: String? = null,
    val releaseTo: String? = null,
    val runtimeMin: Int? = null,
    val runtimeMax: Int? = null,
    val ratingMin: Double? = null,
    val ratingMax: Double? = null,
    val voteCountMin: Int? = null,
    val voteCountMax: Int? = null,
    val providers: Set<Int> = emptySet(),
    val region: String? = null,
    val status: Set<Int> = emptySet(),
    val certificationCountry: String? = null,
    val certification: String? = null,
) {
    /** Whether nothing narrows the list. */
    val isEmpty: Boolean get() = this == DiscoverFilters()
}

/**
 * The query the server's discover route takes for [filters] in [sort], at [page]. A parameter the connected server does not know
 * is left out, as is one that makes no sense for [kind], so no screen has to ask which server it is talking to. A list is
 * joined with a comma, and the streaming services with the pipe TMDB reads as "any of".
 */
fun toDiscoverQuery(
    kind: TitleKind,
    filters: DiscoverFilters,
    sort: DiscoverSort,
    page: Int,
    profile: SeerrServerProfile,
): Map<String, String> {
    val series = kind == TitleKind.Series
    return buildMap {
        put("page", page.toString())
        if (sort.appliesTo(kind)) put("sortBy", sort.value)
        filters.genres.joinedOrNull(",")?.let { put("genre", it) }
        filters.company?.let { put(if (series) "network" else "studio", it.toString()) }
        filters.keywords.joinedOrNull(",")?.let { put("keywords", it) }
        if (profile.hasDiscoverExcludeKeywords) filters.excludeKeywords.joinedOrNull(",")?.let { put("excludeKeywords", it) }
        filters.language?.let { put("language", it) }
        filters.releaseFrom?.let { put(if (series) "firstAirDateGte" else "primaryReleaseDateGte", it) }
        filters.releaseTo?.let { put(if (series) "firstAirDateLte" else "primaryReleaseDateLte", it) }
        filters.runtimeMin?.let { put("withRuntimeGte", it.toString()) }
        filters.runtimeMax?.let { put("withRuntimeLte", it.toString()) }
        filters.ratingMin?.let { put("voteAverageGte", it.toString()) }
        filters.ratingMax?.let { put("voteAverageLte", it.toString()) }
        filters.voteCountMin?.let { put("voteCountGte", it.toString()) }
        filters.voteCountMax?.let { put("voteCountLte", it.toString()) }
        filters.providers.joinedOrNull("|")?.let { providers ->
            put("watchProviders", providers)
            filters.region?.let { put("watchRegion", it) }
        }
        if (series && profile.hasDiscoverStatusFilter) filters.status.joinedOrNull("|")?.let { put("status", it) }
        if (profile.hasDiscoverCertification) {
            val certification = filters.certification
            val country = filters.certificationCountry
            if (certification != null && country != null) {
                put("certificationCountry", country)
                put("certification", certification)
            }
        }
    }
}

private fun Set<Int>.joinedOrNull(separator: String): String? = takeIf { it.isNotEmpty() }?.sorted()?.joinToString(separator)
