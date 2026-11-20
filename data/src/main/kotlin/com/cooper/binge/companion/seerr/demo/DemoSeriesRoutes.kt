package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrCastDto
import com.cooper.binge.companion.seerr.seerr.SeerrCreditsDto
import com.cooper.binge.companion.seerr.seerr.SeerrEpisodeDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMovieWatchProviderDto
import com.cooper.binge.companion.seerr.seerr.SeerrNamedDto
import com.cooper.binge.companion.seerr.seerr.SeerrRottenTomatoesDto
import com.cooper.binge.companion.seerr.seerr.SeerrSeasonDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrTvDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrTvSeasonDto
import com.cooper.binge.companion.seerr.seerr.SeerrVideoDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchRegionDto

private const val DEMO_EPISODE_MINUTES = 24
private const val DEMO_SHOW_VOTES = 860
private const val DEMO_SHOW_VOTE_AVERAGE = 8.1
private const val DEMO_SHOW_CRITICS = 95
private const val DEMO_SHOW_AUDIENCE = 90
private const val DEMO_NEXT_SEASON = 2
private const val DEMO_NEXT_EPISODE = 4
private const val GENRE_DRAMA = 18
private const val RELATED_SHOWS_SKIP = 1
private const val DEMO_EPISODES = 8
private const val DEMO_UNAIRED_FROM = 7
private const val DEMO_CREATOR_ID = 9101
private const val DEMO_LEAD_ID = 9001
private const val DEMO_RIVAL_ID = 9002

/** The series page's reads beyond the thin lookup: recommendations, similar shows and the Rotten Tomatoes score. */
internal fun DemoData.seriesRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "tv", "{}", "recommendations") { call, (id) -> page(call, relatedShows(id, skip = 0)) },
        route("GET", "tv", "{}", "similar") { call, (id) -> page(call, relatedShows(id, skip = RELATED_SHOWS_SKIP)) },
        route("GET", "tv", "{}", "season", "{}") { _, (_, number) -> ok(demoSeason(number.toInt())) },
        route("GET", "tv", "{}", "ratings") { _, _ -> ok(SeerrRottenTomatoesDto(DEMO_SHOW_CRITICS, DEMO_SHOW_AUDIENCE, null)) },
    )

private fun DemoData.relatedShows(
    id: String,
    skip: Int,
): List<DemoTitle> = DEMO_TITLES.filter { !it.isMovie && it.tmdbId != id.toInt() }.drop(skip)

/** The whole series page: the thin lookup's fields, which the exported Service still reads from the same call, plus what the page shows. */
internal fun DemoData.tvDetails(id: String): DemoAnswer {
    val thin = demoJson.decodeFromString<SeerrMediaDetailsDto>(details("tv", id).body)
    return ok(seriesBody(thin, id.toInt()))
}

private fun DemoData.seriesBody(
    thin: SeerrMediaDetailsDto,
    tmdbId: Int,
) = SeerrTvDetailsDto(
    id = tmdbId,
    name = thin.name.orEmpty(),
    tagline = "A demo show, with nothing real behind it.",
    overview = thin.overview,
    posterPath = thin.posterPath,
    firstAirDate = thin.firstAirDate,
    lastAirDate = thin.firstAirDate,
    episodeRunTime = listOf(DEMO_EPISODE_MINUTES),
    status = "Returning Series",
    inProduction = true,
    voteAverage = DEMO_SHOW_VOTE_AVERAGE,
    voteCount = DEMO_SHOW_VOTES,
    numberOfSeasons = thin.seasons.size,
    numberOfEpisodes = thin.seasons.sumOf { it.episodeCount },
    originalLanguage = "en",
    homepage = "https://demo.example.com/shows/$tmdbId",
    genres = listOf(SeerrNamedDto(GENRE_DRAMA, "Drama")),
    keywords = listOf(SeerrNamedDto(1, "demo"), SeerrNamedDto(2, "ensemble")),
    networks = listOf(SeerrNamedDto(1, "Demo Network")),
    createdBy = listOf(SeerrNamedDto(DEMO_CREATOR_ID, "Riley Director")),
    contentRatings = thin.contentRatings,
    seasons = thin.seasons.map { SeerrTvSeasonDto(seasonNumber = it.seasonNumber, name = it.name, episodeCount = it.episodeCount) },
    nextEpisodeToAir =
        SeerrEpisodeDto(name = "The Next One", airDate = "2099-01-01", episodeNumber = DEMO_NEXT_EPISODE, seasonNumber = DEMO_NEXT_SEASON),
    credits =
        SeerrCreditsDto(
            listOf(
                SeerrCastDto(DEMO_LEAD_ID, "Alex Sample", "The Hero", null, 0),
                SeerrCastDto(DEMO_RIVAL_ID, "Sam Example", "The Rival", null, 1),
            ),
        ),
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
    mediaInfo = thin.mediaInfo,
    onUserWatchlist = onWatchlist("tv", tmdbId),
)

/** A season of made-up episodes, the last of which has not aired, so a list shows both kinds. */
private fun demoSeason(number: Int) =
    SeerrSeasonDetailsDto(
        seasonNumber = number,
        name = "Season $number",
        episodes =
            (1..DEMO_EPISODES).map {
                SeerrEpisodeDto(
                    id = number * DEMO_EPISODES + it,
                    name = "Episode $it",
                    overview = "A made-up episode, with nothing real behind it.".takeIf { _ -> it != 2 },
                    airDate = if (it < DEMO_UNAIRED_FROM) "2020-0$it-01" else "2099-01-0${it - DEMO_UNAIRED_FROM + 1}",
                    episodeNumber = it,
                    seasonNumber = number,
                )
            },
    )
