package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrMediaDetailsDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrSeasonDto

private const val MOVIE = "movie"
private const val TV = "tv"
private const val EPISODES_PER_SEASON = 8

/**
 * One title the demo knows (a title outside [DEMO_TITLES] is made up on first use, with no artwork; see [DemoData.titleFor]): a real TMDB id and poster, so artwork loads from TMDB as it does against a real server,
 * with an overview written for the demo. [mediaId] is the server's own id for it, which issues are filed against.
 */
internal class DemoTitle(
    val mediaId: Int,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val year: Int,
    val overview: String,
    val poster: String,
    val seasons: Int = 0,
) {
    val isMovie: Boolean get() = mediaType == MOVIE

    fun details(
        status: SeerrMediaStatusCode,
        info: SeerrMediaInfoDto,
    ) = SeerrMediaDetailsDto(
        mediaInfo = info.copy(id = mediaId, status = status),
        title = title.takeIf { isMovie },
        name = title.takeUnless { isMovie },
        posterPath = poster.ifBlank { null },
        overview = overview.ifBlank { null },
        releaseDate = "$year-01-01".takeIf { isMovie && year > 0 },
        firstAirDate = "$year-01-01".takeIf { !isMovie && year > 0 },
        seasons = (1..seasons).map { SeerrSeasonDto(seasonNumber = it, name = "Season $it", episodeCount = EPISODES_PER_SEASON) },
    )
}

/**
 * Open films and a few others, so a screenshot shows artwork that is free to show. The first six are in the public
 * domain or under a Creative Commons licence.
 */
internal val DEMO_TITLES =
    listOf(
        DemoTitle(
            1,
            10378,
            MOVIE,
            "Big Buck Bunny",
            2008,
            "A giant rabbit takes a quiet revenge on three bullying rodents.",
            "/i9jJzvoXET4D9pOkoEwncSdNNER.jpg",
        ),
        DemoTitle(
            2,
            45745,
            MOVIE,
            "Sintel",
            2010,
            "A young woman searches a harsh land for the dragon she once raised.",
            "/l98Zns4oQe1BSvbF4hJ8oL6VSf0.jpg",
        ),
        DemoTitle(
            3,
            133701,
            MOVIE,
            "Tears of Steel",
            2012,
            "Soldiers and scientists try to save the world from robots in Amsterdam.",
            "/8qy3jRmaHR7f8VZh3iXCqCWfFsH.jpg",
        ),
        DemoTitle(
            4,
            10331,
            MOVIE,
            "Night of the Living Dead",
            1968,
            "Strangers hold out in a farmhouse through one long night.",
            "/rb2NWyb008u1EcKCOyXs2Nmj0ra.jpg",
        ),
        DemoTitle(
            5,
            653,
            MOVIE,
            "Nosferatu",
            1922,
            "A count's journey to a new city brings a plague with him.",
            "/zv7J85D8CC9qYagAEhPM63CIG6j.jpg",
        ),
        DemoTitle(
            6,
            19,
            MOVIE,
            "Metropolis",
            1927,
            "In a city of towers and machines, a son of the rulers crosses to the workers' side.",
            "/kr9wXRN23zLuWJIelahas1mtnYj.jpg",
        ),
        DemoTitle(7, 603, MOVIE, "The Matrix", 1999, "A hacker learns what his world really is.", "/dXNAPwY7VrqMAo51EKhhCJfaGb5.jpg"),
        DemoTitle(
            8,
            129,
            MOVIE,
            "Spirited Away",
            2001,
            "A girl must work in a bathhouse for spirits to free her parents.",
            "/39wmItIWsg5sZMyRUHLkWBcuVCM.jpg",
        ),
        DemoTitle(
            9,
            496243,
            MOVIE,
            "Parasite",
            2019,
            "Two families, one house, and a plan that grows past control.",
            "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg",
        ),
        DemoTitle(
            10,
            63639,
            TV,
            "The Expanse",
            2015,
            "A detective and a ship's crew find a thread that runs through the solar system.",
            "/5vQlVWkIMPhZ88OWchJsgwGEK9.jpg",
            seasons = 6,
        ),
        DemoTitle(
            11,
            95396,
            TV,
            "Severance",
            2022,
            "Office workers have their work lives surgically split from their own.",
            "/pPHpeI2X1qEd1CS1SeyrdhZ4qnT.jpg",
            seasons = 2,
        ),
    )

internal const val TV_TYPE = TV
internal const val MOVIE_TYPE = MOVIE
