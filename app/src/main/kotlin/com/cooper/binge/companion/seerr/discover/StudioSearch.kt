package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.core.search.SearchPage
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.toTmdbLogoUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A production company the search found: its TMDB id, name, and the logo's URL where TMDB has one. */
data class StudioResult(
    val id: Int,
    val name: String,
    val logoUrl: String?,
)

/** TMDB's companies matching a query, one page at a time. Throws as the network call does. */
fun interface StudioSearch {
    suspend fun search(
        query: String,
        page: Int,
    ): SearchPage<StudioResult>
}

/** [StudioSearch] over the server's `/search/company`. [io] is the dispatcher the call runs on; it has no default. */
class SeerrStudioSearch(
    private val api: suspend () -> SeerrApi,
    private val io: CoroutineDispatcher,
) : StudioSearch {
    override suspend fun search(
        query: String,
        page: Int,
    ): SearchPage<StudioResult> =
        withContext(io) {
            val found = api().searchCompanies(query, page)
            SearchPage(
                items =
                    found.results.mapNotNull { dto ->
                        dto.name?.takeIf { it.isNotBlank() }?.let { StudioResult(dto.id, it, dto.logoPath?.toTmdbLogoUrl()) }
                    },
                page = found.page,
                totalPages = found.totalPages,
                total = found.totalResults,
            )
        }
}
