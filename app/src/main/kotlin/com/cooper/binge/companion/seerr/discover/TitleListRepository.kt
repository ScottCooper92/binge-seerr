package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.toDiscoverQuery
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrGenreSliderDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrTitlePageDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The lists of titles the discovery screens read: the hubs' rows, the grid and search. It knows nothing of Retrofit, so a
 * view model holds this and a test holds a fake.
 */
interface TitleListRepository {
    /** Whether the connected server has [source]; a screen asks this and hides what the server lacks. */
    suspend fun supports(source: TitleSource): Boolean

    /** One page of [source]. Throws as the network call does; [source] must be [supports]ed. */
    suspend fun load(
        source: TitleSource,
        page: Int,
    ): TitlePage

    /** The genre tiles for [kind] (a movie or a series), empty where the server has no genre row. */
    suspend fun genres(kind: TitleKind): List<GenreTile>
}

/** [TitleListRepository] over the Seerr API. [io] is the dispatcher the calls run on; it has no default. */
class SeerrTitleListRepository(
    private val api: suspend () -> SeerrApi,
    private val profile: suspend () -> SeerrServerProfile,
    private val io: CoroutineDispatcher,
) : TitleListRepository {
    override suspend fun supports(source: TitleSource): Boolean = source.supportedBy(profile())

    override suspend fun load(
        source: TitleSource,
        page: Int,
    ): TitlePage {
        require(supports(source)) { "The server has no such list" }
        // Without a query the server fails the call instead of answering an empty page.
        if (source is TitleSource.Search && source.query.isBlank()) return TitlePage(emptyList(), page = 1, totalPages = 1)
        val profile = profile()
        return withContext(io) { api().fetch(source, page, profile).toTitlePage(source.defaultKind) }
    }

    override suspend fun genres(kind: TitleKind): List<GenreTile> {
        if (!profile().hasGenreSliders) return emptyList()
        return withContext(io) {
            val api = api()
            val tiles: List<SeerrGenreSliderDto> = if (kind == TitleKind.Series) api.tvGenreSlider() else api.movieGenreSlider()
            tiles.map { GenreTile(it.id, it.name, it.backdrops.firstOrNull()) }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private suspend fun SeerrApi.fetch(
    source: TitleSource,
    page: Int,
    profile: SeerrServerProfile,
): SeerrTitlePageDto =
    when (source) {
        TitleSource.Trending -> discoverTrending(page)
        TitleSource.Watchlist -> discoverWatchlist(page)
        is TitleSource.Search -> searchTitles(source.query, page)
        is TitleSource.Popular -> discover(source.kind, DiscoverFilters(), page, profile)
        is TitleSource.Upcoming -> if (source.kind == TitleKind.Series) discoverUpcomingTv(page) else discoverUpcomingMovies(page)
        is TitleSource.ByGenre ->
            if (source.kind == TitleKind.Series) discoverTvByGenre(source.genreId, page) else discoverMoviesByGenre(source.genreId, page)
        is TitleSource.ByCompany ->
            if (source.kind == TitleKind.Series) {
                discoverTvByNetwork(source.companyId, page)
            } else {
                discoverMoviesByStudio(source.companyId, page)
            }
        is TitleSource.ByLanguage ->
            if (source.kind == TitleKind.Series) {
                discoverTvByLanguage(source.language, page)
            } else {
                discoverMoviesByLanguage(source.language, page)
            }
        is TitleSource.ByKeywords -> discover(source.kind, DiscoverFilters(keywords = source.keywordIds.toSet()), page, profile)
        is TitleSource.ByStreaming ->
            discover(source.kind, DiscoverFilters(providers = source.providerIds.toSet(), region = source.region), page, profile)
        is TitleSource.ByKeyword ->
            if (source.kind == TitleKind.Series) {
                discover(source.kind, DiscoverFilters(keywords = setOf(source.keywordId)), page, profile)
            } else {
                discoverMoviesByKeyword(source.keywordId, page)
            }
    }

/** The server's plain `discover/{movies,tv}` route, asked as the grid asks it: through [toDiscoverQuery], the one mapping to its parameters. */
private suspend fun SeerrApi.discover(
    kind: TitleKind,
    filters: DiscoverFilters,
    page: Int,
    profile: SeerrServerProfile,
): SeerrTitlePageDto {
    val query = toDiscoverQuery(kind, filters, DiscoverSort.PopularityDesc, page, profile)
    return if (kind == TitleKind.Series) discoverTvFiltered(query) else discoverMoviesFiltered(query)
}
