package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.title.streamingRegion
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Which of the filters beyond the common ones the connected server takes. */
data class FilterCapabilities(
    val watch: Boolean = false,
    val certification: Boolean = false,
    val keywords: Boolean = false,
    val excludeKeywords: Boolean = false,
    val status: Boolean = false,
)

/** The lookups behind the filter sheet's pickers that need a call of their own. It knows nothing of Retrofit. */
interface FilterLookups {
    suspend fun capabilities(): FilterCapabilities

    /** The countries TMDB has streaming providers for. */
    suspend fun regions(): List<FilterOption>

    /** The region the title page reads providers in: the user's own, then the server's. Null where there is none. */
    suspend fun defaultRegion(): String?

    /** The streaming providers for [kind] in [region], by TMDB id. */
    suspend fun providers(
        kind: TitleKind,
        region: String,
    ): List<FilterOption>

    /** The age ratings of each country's system for [kind], most open first, by country code. */
    suspend fun certifications(kind: TitleKind): Map<String, List<String>>

    suspend fun searchKeywords(query: String): List<FilterOption>

    /** The names of the keywords with these TMDB [ids]; one the server cannot name is left out. */
    suspend fun keywordNames(ids: Set<Int>): Map<Int, String>
}

/** [FilterLookups] over the Seerr API; [viewer] is the signed-in user, whose own region is read first. */
class SeerrFilterLookups(
    private val api: suspend () -> SeerrApi,
    private val profile: suspend () -> SeerrServerProfile,
    private val viewer: suspend () -> SeerrUserDto,
    private val io: CoroutineDispatcher,
) : FilterLookups {
    override suspend fun capabilities(): FilterCapabilities =
        profile().let {
            FilterCapabilities(
                watch = it.hasWatchProviderDiscover,
                certification = it.hasDiscoverCertification,
                keywords = it.hasKeywordDiscover,
                excludeKeywords = it.hasDiscoverExcludeKeywords,
                status = it.hasDiscoverStatusFilter,
            )
        }

    override suspend fun regions(): List<FilterOption> =
        withContext(io) {
            api()
                .watchProviderRegions()
                .mapNotNull { region -> region.englishName?.takeIf { it.isNotBlank() }?.let { FilterOption(region.code, it) } }
                .sortedBy { it.name }
        }

    override suspend fun defaultRegion(): String? = withContext(io) { streamingRegion(api(), viewer(), profile()) }

    override suspend fun providers(
        kind: TitleKind,
        region: String,
    ): List<FilterOption> =
        withContext(io) {
            api()
                .watchProviders(if (kind == TitleKind.Series) "tv" else "movies", region)
                .mapNotNull { provider -> provider.name?.takeIf { it.isNotBlank() }?.let { FilterOption(provider.id.toString(), it) } }
                .sortedBy { it.name }
        }

    override suspend fun certifications(kind: TitleKind): Map<String, List<String>> =
        withContext(io) {
            api()
                .certifications(if (kind == TitleKind.Series) "tv" else "movie")
                .certifications
                .mapValues { (_, ratings) -> ratings.sortedBy { it.order }.map { it.certification }.filter { it.isNotBlank() } }
                .filterValues { it.isNotEmpty() }
        }

    override suspend fun searchKeywords(query: String): List<FilterOption> =
        withContext(io) {
            api().searchKeywords(query.trim()).results.mapNotNull { keyword ->
                keyword.name?.takeIf { it.isNotBlank() }?.let { FilterOption(keyword.id.toString(), it) }
            }
        }

    override suspend fun keywordNames(ids: Set<Int>): Map<Int, String> =
        withContext(io) {
            val api = api()
            ids.mapNotNull { id -> api.keyword(id).name?.let { id to it } }.toMap()
        }
}
