package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrGenreSliderDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrTitlePageDto
import com.cooper.binge.companion.seerr.seerr.SeerrTitleResultDto

private const val DISCOVER_PAGE_SIZE = 20
private const val TV_TYPE_NAME = "tv"

/**
 * The discover lists and search, over the demo's own titles with the status their requests give them. The demo has no genre,
 * studio, network, language or keyword data, so each of those lists is the whole movie or show list.
 */
internal fun DemoData.discoverRoutes(): List<DemoRoute> =
    listOf(
        route(
            "GET",
            "discover",
            "trending",
        ) { call, _ -> page(call, titles(movie = true).interleave(titles(movie = false))) },
        route("GET", "discover", "movies") { call, _ -> page(call, titles(movie = true)) },
        route("GET", "discover", "tv") { call, _ -> page(call, titles(movie = false)) },
        route("GET", "discover", "movies", "upcoming") { call, _ -> page(call, titles(movie = true)) },
        route("GET", "discover", "tv", "upcoming") { call, _ -> page(call, titles(movie = false)) },
        route("GET", "discover", "movies", "genre", "{}") { call, _ -> page(call, titles(movie = true)) },
        route("GET", "discover", "tv", "genre", "{}") { call, _ -> page(call, titles(movie = false)) },
        route("GET", "discover", "movies", "studio", "{}") { call, _ -> page(call, titles(movie = true)) },
        route("GET", "discover", "tv", "network", "{}") { call, _ -> page(call, titles(movie = false)) },
        route("GET", "discover", "movies", "language", "{}") { call, _ -> page(call, titles(movie = true)) },
        route("GET", "discover", "tv", "language", "{}") { call, _ -> page(call, titles(movie = false)) },
        route("GET", "discover", "keyword", "{}", "movies") { call, _ -> page(call, titles(movie = true)) },
        route("GET", "discover", "genreslider", "{}") { _, _ -> ok(GENRE_TILES) },
        route("GET", "discover", "watchlist") { call, _ -> page(call, titles(movie = true).take(WATCHLIST_SIZE)) },
        route("GET", "search") { call, _ -> searchPage(call) },
    )

private const val WATCHLIST_SIZE = 3

private val GENRE_TILES =
    listOf(
        SeerrGenreSliderDto(28, "Action", listOf("/i9jJzvoXET4D9pOkoEwncSdNNER.jpg")),
        SeerrGenreSliderDto(16, "Animation", listOf("/l98Zns4oQe1BSvbF4hJ8oL6VSf0.jpg")),
        SeerrGenreSliderDto(35, "Comedy", listOf("/8qy3jRmaHR7f8VZh3iXCqCWfFsH.jpg")),
        SeerrGenreSliderDto(18, "Drama", listOf("/rb2NWyb008u1EcKCOyXs2Nmj0ra.jpg")),
    )

private fun titles(movie: Boolean): List<DemoTitle> = DEMO_TITLES.filter { it.isMovie == movie }

private fun found(call: DemoCall): List<DemoTitle> {
    val query = call.query("query").orEmpty().trim()
    return DEMO_TITLES.filter { query.isNotEmpty() && it.title.contains(query, ignoreCase = true) }
}

/** The people the demo's search can find: invented names with no photo, since the demo holds no real person. */
private val DEMO_PEOPLE =
    listOf(
        SeerrTitleResultDto(id = 9001, mediaType = "person", name = "Marlowe Quinn"),
        SeerrTitleResultDto(id = 9002, mediaType = "person", name = "Ines Valdemar"),
        SeerrTitleResultDto(id = 9003, mediaType = "person", name = "Tobias Hallworth"),
    )

/** Search pages are short, so a broad query in the demo has a second page to load. */
private const val SEARCH_PAGE_SIZE = 3

/** Titles, then people, matching the query, [SEARCH_PAGE_SIZE] to a page. */
private fun DemoData.searchPage(call: DemoCall): DemoAnswer {
    val query = call.query("query").orEmpty().trim()
    val all =
        found(call).map { toResult(it) } + DEMO_PEOPLE.filter { query.isNotEmpty() && it.name.orEmpty().contains(query, ignoreCase = true) }
    val page = call.intQuery("page", 1).coerceAtLeast(1)
    val pages = ((all.size + SEARCH_PAGE_SIZE - 1) / SEARCH_PAGE_SIZE).coerceAtLeast(1)
    val rows = all.drop((page - 1) * SEARCH_PAGE_SIZE).take(SEARCH_PAGE_SIZE)
    return ok(SeerrTitlePageDto(page = page, totalPages = pages, totalResults = all.size, results = rows))
}

private fun <T> List<T>.interleave(other: List<T>): List<T> =
    (0 until maxOf(size, other.size)).flatMap { listOfNotNull(getOrNull(it), other.getOrNull(it)) }

/** One page of [all], each row with the media type every server sends. */
private fun DemoData.page(
    call: DemoCall,
    all: List<DemoTitle>,
): DemoAnswer {
    val page = call.intQuery("page", 1).coerceAtLeast(1)
    val pages = ((all.size + DISCOVER_PAGE_SIZE - 1) / DISCOVER_PAGE_SIZE).coerceAtLeast(1)
    val rows = all.drop((page - 1) * DISCOVER_PAGE_SIZE).take(DISCOVER_PAGE_SIZE).map { toResult(it) }
    return ok(SeerrTitlePageDto(page = page, totalPages = pages, totalResults = all.size, results = rows))
}

private fun DemoData.toResult(title: DemoTitle): SeerrTitleResultDto =
    SeerrTitleResultDto(
        id = title.tmdbId,
        mediaType = if (title.isMovie) "movie" else TV_TYPE_NAME,
        title = title.title.takeIf { title.isMovie },
        name = title.title.takeUnless { title.isMovie },
        posterPath = title.poster.ifBlank { null },
        overview = title.overview,
        releaseDate = "${title.year}-01-01".takeIf { title.isMovie },
        firstAirDate = "${title.year}-01-01".takeUnless { title.isMovie },
        voteAverage = DEMO_VOTE,
        mediaInfo = SeerrMediaInfoDto(id = title.mediaId, status = mediaStatus(title)),
    )

private const val DEMO_VOTE = 7.4
