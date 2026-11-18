package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.NextEpisode
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleProvider
import com.cooper.binge.companion.seerr.discover.title.TitleProviders
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.TitleVideo
import com.cooper.binge.companion.seerr.discover.title.VersionState

private const val EPISODES = 10
private const val RUNTIME = 55
private const val CRITICS = 95
private const val AUDIENCE = 90
private const val VOTES = 12_000
private const val FIRST_YEAR = 2011
private const val NEXT_SEASON = 4

internal fun season(
    number: Int,
    standard: TitleAvailability,
    name: String = "Season $number",
) = SeasonInfo(number, name, EPISODES, "${FIRST_YEAR + number - 1}", standard, fourK = null)

/** A show with a season in each state a season can be in. */
internal fun mixedSeasons() =
    listOf(
        season(1, TitleAvailability.Available),
        season(2, TitleAvailability.PartiallyAvailable),
        season(3, TitleAvailability.Processing),
        season(NEXT_SEASON, TitleAvailability.Pending),
        season(NEXT_SEASON + 1, TitleAvailability.None),
    )

private fun person(
    id: Int,
    name: String,
    role: String,
) = TitlePerson(id, name, role, null)

/** A show with everything the server gives and a season still open to request. */
internal fun fullSeries(
    name: String = "The Long Winter",
    seasons: List<SeasonInfo> = mixedSeasons(),
    standard: VersionState = VersionState(TitleAvailability.PartiallyAvailable, mayRequest = true),
) = SeriesDetails(
    tmdbId = 1399,
    name = name,
    tagline = "Every season has its end",
    overview = "A family keeps a border town alive through the longest winter anyone remembers, one season at a time.",
    posterUrl = null,
    backdropUrl = null,
    firstAirYear = "$FIRST_YEAR",
    firstAirDate = "$FIRST_YEAR-04-17",
    lastAirDate = null,
    episodeRuntimeMinutes = RUNTIME,
    certification = "TV-MA",
    voteAverage = 8.4,
    voteCount = VOTES,
    genres = listOf("Drama", "Fantasy"),
    keywords = listOf("winter", "family", "kingdom"),
    status = "Returning Series",
    inProduction = true,
    originalLanguage = "en",
    spokenLanguageCodes = listOf("en", "es"),
    networks = listOf(TitleCompany(1, "Demo Network")),
    creators = listOf("Riley Director"),
    seasonCount = seasons.size,
    episodeCount = seasons.sumOf { it.episodeCount },
    nextEpisode = NextEpisode(NEXT_SEASON, 1, "The Thaw", "2099-01-01"),
    seasons = seasons,
    cast = listOf(person(1, "Alex Sample", "The Warden"), person(2, "Sam Example", "The Heir"), person(3, "Jordan Demo", "The Cook")),
    keyCrew = listOf(person(10, "Riley Director", "Executive Producer"), person(11, "Casey Writer", "Writer")),
    otherCrew = emptyList(),
    videos = listOf(TitleVideo("Official Trailer", "https://www.youtube.com/watch?v=demo", isTrailer = true)),
    providers = TitleProviders("US", null, listOf(TitleProvider(1, "Demo Stream", null)), listOf(TitleProvider(2, "Demo Store", null))),
    standard = standard,
    fourK = null,
    partialRequests = true,
    mediaId = 11,
    webUrl = "https://seerr.example/tv/1399",
)

/** A show of which the server knows almost nothing: every empty section is left out. */
internal fun sparseSeries() =
    fullSeries(seasons = listOf(season(1, TitleAvailability.None))).copy(
        tagline = null,
        overview = null,
        genres = emptyList(),
        keywords = emptyList(),
        status = null,
        networks = emptyList(),
        creators = emptyList(),
        nextEpisode = null,
        voteAverage = null,
        videos = emptyList(),
        providers = null,
        cast = emptyList(),
        keyCrew = emptyList(),
        originalLanguage = null,
        spokenLanguageCodes = emptyList(),
        episodeRuntimeMinutes = null,
        certification = null,
    )

/** The longest names the page has to hold, over a season of every state. */
internal fun longSeries() =
    fullSeries(
        name = "The Chronicles of the Extraordinarily Long-Winded Winter Kingdom and Its Many Peculiar Inhabitants",
        seasons =
            mixedSeasons().mapIndexed { index, season ->
                if (index == 0) season.copy(name = "The Extended Special Collector's Edition Season With A Very Long Name") else season
            },
    ).copy(
        networks =
            listOf(
                TitleCompany(1, "A Very Long Premium Cable Network"),
                TitleCompany(2, "Another Streaming Service Of Considerable Length"),
            ),
        creators = listOf("Christopher Montgomery-Fitzgerald", "Alexandria Wolfeschlegelsteinhausen"),
    )

internal fun seriesExtras() =
    TitleExtras(
        ratings = TitleRatings(CRITICS, AUDIENCE, null),
        recommendations =
            listOf(
                TitleResult(TitleKind.Series, 2, "The Short Summer", "2015", null, null, null, null, TitleAvailability.None, null),
            ),
        similar =
            listOf(
                TitleResult(TitleKind.Series, 3, "The Long Night", "2019", null, null, null, null, TitleAvailability.Available, null),
            ),
    )

private const val EPISODE_COUNT = 8
private const val LONG_EPISODE_NAME = "The One Where Everyone Finally Finds Out What Happened At The Extraordinarily Long Winter Festival"
private const val UNAIRED_FROM = 7

/** A season's episodes, the last of which has not aired, one without an overview and one without a still. */
internal fun episodes(
    count: Int = EPISODE_COUNT,
    longNames: Boolean = false,
) = (1..count).map {
    SeasonEpisode(
        number = it,
        name = if (longNames) "$LONG_EPISODE_NAME $it" else "Episode $it",
        overview = "A made-up episode, in which a family keeps a border town alive for one more night.".takeIf { _ -> it != 2 },
        airDate = if (it < UNAIRED_FROM) "2020-0$it-01" else "2099-01-0${it - UNAIRED_FROM + 1}",
        stillUrl = null,
        rating = (RATING_BASE + it).takeIf { _ -> it != 2 },
    )
}

private const val RATING_BASE = 6.5f

/** The first season selected with its episodes read. */
internal fun readyWithEpisodes(
    series: SeriesDetails = fullSeries(),
    longNames: Boolean = false,
) = SeriesPageUiState.Ready(
    series,
    seriesExtras(),
    selectedSeason = 1,
    episodes =
        mapOf(
            1 to EpisodesState.Ready(episodes(longNames = longNames)),
        ),
)
