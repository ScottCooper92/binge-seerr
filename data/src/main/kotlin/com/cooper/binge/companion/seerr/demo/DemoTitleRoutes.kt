package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrCastDto
import com.cooper.binge.companion.seerr.seerr.SeerrCollectionDto
import com.cooper.binge.companion.seerr.seerr.SeerrCollectionRefDto
import com.cooper.binge.companion.seerr.seerr.SeerrCountryReleasesDto
import com.cooper.binge.companion.seerr.seerr.SeerrCreditsDto
import com.cooper.binge.companion.seerr.seerr.SeerrCrewDto
import com.cooper.binge.companion.seerr.seerr.SeerrImdbDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMovieDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMovieWatchProviderDto
import com.cooper.binge.companion.seerr.seerr.SeerrNamedDto
import com.cooper.binge.companion.seerr.seerr.SeerrRatingsDto
import com.cooper.binge.companion.seerr.seerr.SeerrReleaseDateDto
import com.cooper.binge.companion.seerr.seerr.SeerrReleasesDto
import com.cooper.binge.companion.seerr.seerr.SeerrRottenTomatoesDto
import com.cooper.binge.companion.seerr.seerr.SeerrVideoDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchRegionDto

private const val DEMO_RUNTIME = 96
private const val DEMO_VOTES = 1200
private const val GENRE_ANIMATION = 16
private const val GENRE_COMEDY = 35
private const val DEMO_VOTE_AVERAGE = 7.4
private const val DEMO_CRITICS = 92
private const val DEMO_AUDIENCE = 88
private const val DEMO_IMDB = 7.9
private const val RELATED_SKIP_SIMILAR = 2
private const val DEMO_COLLECTION_ID = 9500
private const val DEMO_COLLECTION_FILMS = 3
private const val DEMO_COLLECTION_NAME = "The Demo Collection"

/** Hand-authored people and places, so no real name or address is in the demo. */
private val DEMO_CAST =
    listOf(
        SeerrCastDto(9001, "Alex Sample", "The Hero", null, 0),
        SeerrCastDto(9002, "Sam Example", "The Rival", null, 1),
        SeerrCastDto(9003, "Jordan Demo", "A Friend", null, 2),
    )
private val DEMO_CREW =
    listOf(
        SeerrCrewDto(9101, "Riley Director", "Director", "Directing", null),
        SeerrCrewDto(9102, "Casey Writer", "Screenplay", "Writing", null),
        SeerrCrewDto(9102, "Casey Writer", "Writer", "Writing", null),
        SeerrCrewDto(9103, "Morgan Composer", "Original Music Composer", "Sound", null),
    )

/** The first few demo films, which the demo makes one collection so the film page's rail has something to show. */
private val DEMO_COLLECTION = DEMO_TITLES.filter { it.isMovie }.take(DEMO_COLLECTION_FILMS)

/** The film page's reads beyond the thin lookup: its collection, recommendations, similar titles and the combined ratings. */
internal fun DemoData.titleRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "collection", "{}") { _, (id) -> collection(id.toIntOrNull()) },
        route("POST", "blocklist", "collection", "{}") { _, (id) -> setCollectionBlocked(id.toIntOrNull(), blocked = true) },
        route("DELETE", "blocklist", "collection", "{}") { _, (id) -> setCollectionBlocked(id.toIntOrNull(), blocked = false) },
        route("GET", "movie", "{}", "recommendations") { call, (id) -> page(call, related(id, skip = 1)) },
        route("GET", "movie", "{}", "similar") { call, (id) -> page(call, related(id, skip = RELATED_SKIP_SIMILAR)) },
        route("GET", "movie", "{}", "ratingscombined") { _, _ ->
            ok(SeerrRatingsDto(SeerrRottenTomatoesDto(DEMO_CRITICS, DEMO_AUDIENCE, null), SeerrImdbDto(DEMO_IMDB, null)))
        },
    )

/** Blocking a collection blocks each of its films, as the server does; unblocking lifts each one. */
private fun DemoData.setCollectionBlocked(
    id: Int?,
    blocked: Boolean,
): DemoAnswer {
    if (id != DEMO_COLLECTION_ID) return demoNotFound
    DEMO_COLLECTION.forEach { if (blocked) block(it, DEMO_USER_ID) else unblock(it.tmdbId) }
    return done
}

private fun DemoData.collection(id: Int?): DemoAnswer =
    if (id != DEMO_COLLECTION_ID) {
        demoNotFound
    } else {
        ok(
            SeerrCollectionDto(
                id = DEMO_COLLECTION_ID,
                name = DEMO_COLLECTION_NAME,
                overview = "Three demo films that belong together.",
                posterPath = DEMO_COLLECTION.firstOrNull()?.poster,
                parts = DEMO_COLLECTION.map { toResult(it) },
            ),
        )
    }

private fun DemoData.related(
    id: String,
    skip: Int,
): List<DemoTitle> = DEMO_TITLES.filter { it.isMovie && it.tmdbId != id.toInt() }.drop(skip)

/** The whole film page: the thin lookup's fields, which the exported Service still reads from the same call, plus what the page shows. */
internal fun DemoData.movieDetails(id: String): DemoAnswer {
    val thin = demoJson.decodeFromString<SeerrMediaDetailsDto>(details("movie", id).body)
    return ok(titleBody(thin, id.toInt()))
}

private fun DemoData.titleBody(
    thin: SeerrMediaDetailsDto,
    tmdbId: Int,
) = SeerrMovieDetailsDto(
    id = tmdbId,
    title = thin.title.orEmpty(),
    tagline = "A demo title, with nothing real behind it.",
    overview = thin.overview,
    posterPath = thin.posterPath,
    releaseDate = thin.releaseDate,
    runtime = DEMO_RUNTIME,
    status = "Released",
    voteAverage = DEMO_VOTE_AVERAGE,
    voteCount = DEMO_VOTES,
    originalLanguage = "en",
    genres = listOf(SeerrNamedDto(GENRE_ANIMATION, "Animation"), SeerrNamedDto(GENRE_COMEDY, "Comedy")),
    keywords = listOf(SeerrNamedDto(1, "demo"), SeerrNamedDto(2, "short film")),
    productionCompanies = listOf(SeerrNamedDto(2, "Demo Studios")),
    releases = SeerrReleasesDto(listOf(SeerrCountryReleasesDto("US", listOf(SeerrReleaseDateDto("PG"))))),
    credits = SeerrCreditsDto(DEMO_CAST, DEMO_CREW),
    relatedVideos = listOf(SeerrVideoDto("demo", "Trailer", "YouTube", "Trailer", "https://www.youtube.com/watch?v=demo")),
    watchProviders =
        listOf(
            SeerrWatchRegionDto(
                "US",
                null,
                listOf(SeerrMovieWatchProviderDto(1, "Demo Stream", null, 1)),
                listOf(SeerrMovieWatchProviderDto(2, "Demo Store", null, 1)),
            ),
        ),
    collection = collectionOf(tmdbId),
    mediaInfo = thin.mediaInfo,
    onUserWatchlist = onWatchlist("movie", tmdbId),
)

/** The collection a demo film belongs to: the first few demo films share one, and no other has any. */
private fun collectionOf(tmdbId: Int) =
    DEMO_COLLECTION.firstOrNull { it.tmdbId == tmdbId }?.let {
        SeerrCollectionRefDto(DEMO_COLLECTION_ID, DEMO_COLLECTION_NAME, DEMO_COLLECTION.first().poster)
    }
