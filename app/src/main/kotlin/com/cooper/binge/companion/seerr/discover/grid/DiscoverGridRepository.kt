package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.toTitlePage
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What the grid asks for: [preset] is where it was opened from, and [filters] and [sort] are what the user has made of it. */
data class GridRequest(
    val kind: TitleKind,
    val preset: GridPreset,
    val filters: DiscoverFilters,
    val sort: DiscoverSort,
)

/** The paged lists behind the "see all" grid. It knows nothing of Retrofit, so a view model holds this and a test holds a fake. */
interface DiscoverGridRepository {
    /** Whether the connected server lets [preset] be sorted and filtered, or answers it as one plain list. */
    suspend fun canRefine(preset: GridPreset): Boolean

    /** One page of [request]; throws as the network call does. */
    suspend fun load(
        request: GridRequest,
        page: Int,
    ): TitlePage
}

/**
 * [DiscoverGridRepository] over the Seerr API. A server with the filter parameters is asked for [GridRequest.filters] and
 * [GridRequest.sort] on `discover/{movies,tv}`. One without them, and the lists that take none, are asked for the preset's own
 * route through [titles], so a "see all" still opens, unsorted and unfiltered.
 */
class SeerrDiscoverGridRepository(
    private val api: suspend () -> SeerrApi,
    private val profile: suspend () -> SeerrServerProfile,
    private val titles: TitleListRepository,
    private val io: CoroutineDispatcher,
) : DiscoverGridRepository {
    override suspend fun canRefine(preset: GridPreset): Boolean = preset.isFilterable && profile().hasDiscoverFilters

    override suspend fun load(
        request: GridRequest,
        page: Int,
    ): TitlePage {
        if (!canRefine(request.preset)) return titles.load(request.preset.toSource(request.kind), page)
        val query = toDiscoverQuery(request.kind, request.filters, request.sort, page, profile())
        return withContext(io) {
            val api = api()
            val dto = if (request.kind == TitleKind.Series) api.discoverTvFiltered(query) else api.discoverMoviesFiltered(query)
            dto.toTitlePage(request.kind)
        }
    }
}
