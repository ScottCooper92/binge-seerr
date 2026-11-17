package com.cooper.binge.companion.seerr.discover

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
        return withContext(io) { api().fetch(source, page).toTitlePage(source.defaultKind) }
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
): SeerrTitlePageDto =
    when (source) {
        TitleSource.Trending -> discoverTrending(page)
        TitleSource.Watchlist -> discoverWatchlist(page)
        is TitleSource.Search -> searchTitles(source.query, page)
        is TitleSource.Popular ->
            if (source.kind == TitleKind.Series) discoverTv(page, source.sortBy) else discoverMovies(page, source.sortBy)
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
        is TitleSource.ByKeywords -> {
            val keywords = source.keywordIds.joinToString(",")
            if (source.kind == TitleKind.Series) discoverTv(page, keywords = keywords) else discoverMovies(page, keywords = keywords)
        }
        is TitleSource.ByStreaming -> {
            val providers = source.providerIds.joinToString("|")
            if (source.kind == TitleKind.Series) {
                discoverTv(page, watchRegion = source.region, watchProviders = providers)
            } else {
                discoverMovies(page, watchRegion = source.region, watchProviders = providers)
            }
        }
        is TitleSource.ByKeyword ->
            if (source.kind == TitleKind.Series) {
                discoverTv(page, keywords = source.keywordId.toString())
            } else {
                discoverMoviesByKeyword(source.keywordId, page)
            }
    }
