package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import kotlinx.serialization.Serializable

/**
 * What narrows a discover list, in the app's own terms. Each field is one parameter of the server's `discover/{movies,tv}` route
 * and an unset one is not sent. [releaseFrom] and [releaseTo] are ISO dates (`2024-01-31`), the release date of a movie and the
 * first air date of a show. [company] is a studio for a movie and a network for a show.
 */
@Serializable
data class DiscoverFilters(
    val genres: Set<Int> = emptySet(),
    val genresAny: Boolean = false,
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
    val certificationLte: String? = null,
    val certificationGte: String? = null,
) {
    /** Whether nothing narrows the list. */
    val isEmpty: Boolean get() = copy(genresAny = genresAny && genres.isNotEmpty()) == DiscoverFilters()
}

/** Whether every filter a [preset] set is still set here, so the chip that stands for the preset still reads true. */
fun DiscoverFilters.keeps(preset: DiscoverFilters): Boolean =
    genres.containsAll(preset.genres) &&
        keywords.containsAll(preset.keywords) &&
        providers.containsAll(preset.providers) &&
        (preset.company == null || company == preset.company) &&
        (preset.language == null || language == preset.language) &&
        (preset.region == null || region == preset.region) &&
        (preset.releaseFrom == null || releaseFrom == preset.releaseFrom)

/**
 * These filters without the ones a [preset] set, so what the user added on top stays. A region stays while a service added
 * on top remains, as a service means nothing without the region it was picked in.
 */
fun DiscoverFilters.without(preset: DiscoverFilters): DiscoverFilters =
    copy(
        genres = genres - preset.genres,
        keywords = keywords - preset.keywords,
        providers = providers - preset.providers,
        company = company.takeUnless { it == preset.company },
        language = language.takeUnless { it == preset.language },
        region = region.takeUnless { it == preset.region && (providers - preset.providers).isEmpty() },
        releaseFrom = releaseFrom.takeUnless { it == preset.releaseFrom },
    )

/** The vote count a list sorted by rating needs, as TMDB's own "top rated" page asks, so a title with a few votes cannot top it. */
const val RATING_SORT_VOTE_FLOOR = 300

/**
 * The query the server's discover route takes for [filters] in [sort], at [page]. A parameter the connected server does not know
 * is left out, as is one that makes no sense for [kind], so no screen has to ask which server it is talking to. A list is
 * joined with a comma, TMDB's "all of", and the streaming services with the pipe it reads as "any of". Genres take the pipe too
 * when [DiscoverFilters.genresAny] is set.
 */
internal fun toDiscoverQuery(
    kind: TitleKind,
    filters: DiscoverFilters,
    sort: DiscoverSort,
    page: Int,
    profile: SeerrServerProfile,
): Map<String, String> =
    buildMap {
        put("page", page.toString())
        if (sort.appliesTo(kind)) put("sortBy", sort.value)
        putWhat(kind, filters)
        putWhen(kind, filters)
        putRanges(filters)
        putWhere(filters)
        putForServer(kind, filters, profile)
    }

/** What a title is about: genres, the studio or network, the keywords and the original language. */
private fun MutableMap<String, String>.putWhat(
    kind: TitleKind,
    filters: DiscoverFilters,
) {
    filters.genres.joinedOrNull(if (filters.genresAny) "|" else ",")?.let { put("genre", it) }
    filters.company?.let { put(if (kind == TitleKind.Series) "network" else "studio", it.toString()) }
    filters.keywords.joinedOrNull(",")?.let { put("keywords", it) }
    filters.language?.let { put("language", it) }
}

/** When it came out: the release date of a movie and the first air date of a show. */
private fun MutableMap<String, String>.putWhen(
    kind: TitleKind,
    filters: DiscoverFilters,
) {
    val series = kind == TitleKind.Series
    filters.releaseFrom?.let { put(if (series) "firstAirDateGte" else "primaryReleaseDateGte", it) }
    filters.releaseTo?.let { put(if (series) "firstAirDateLte" else "primaryReleaseDateLte", it) }
}

/** How long it runs, how well it is rated and how many have voted. */
private fun MutableMap<String, String>.putRanges(filters: DiscoverFilters) {
    filters.runtimeMin?.let { put("withRuntimeGte", it.toString()) }
    filters.runtimeMax?.let { put("withRuntimeLte", it.toString()) }
    filters.ratingMin?.let { put("voteAverageGte", it.toString()) }
    filters.ratingMax?.let { put("voteAverageLte", it.toString()) }
    filters.voteCountMin?.let { put("voteCountGte", it.toString()) }
    filters.voteCountMax?.let { put("voteCountLte", it.toString()) }
}

/** Where to watch it: the streaming services, and the region they are read in, which means nothing without one. */
private fun MutableMap<String, String>.putWhere(filters: DiscoverFilters) {
    val providers = filters.providers.joinedOrNull("|") ?: return
    put("watchProviders", providers)
    filters.region?.let { put("watchRegion", it) }
}

/** The parameters only some servers take: excluded keywords, a show's status and the certification. */
private fun MutableMap<String, String>.putForServer(
    kind: TitleKind,
    filters: DiscoverFilters,
    profile: SeerrServerProfile,
) {
    if (profile.hasDiscoverExcludeKeywords) filters.excludeKeywords.joinedOrNull(",")?.let { put("excludeKeywords", it) }
    if (kind == TitleKind.Series && profile.hasDiscoverStatusFilter) filters.status.joinedOrNull("|")?.let { put("status", it) }
    val lte = filters.certificationLte
    val gte = filters.certificationGte
    val country = filters.certificationCountry
    if (!profile.hasDiscoverCertification || country == null) return
    if (lte == null && gte == null) return
    put("certificationCountry", country)
    put("certificationMode", "range")
    lte?.let { put("certificationLte", it) }
    gte?.let { put("certificationGte", it) }
}

private fun Set<Int>.joinedOrNull(separator: String): String? = takeIf { it.isNotEmpty() }?.sorted()?.joinToString(separator)
