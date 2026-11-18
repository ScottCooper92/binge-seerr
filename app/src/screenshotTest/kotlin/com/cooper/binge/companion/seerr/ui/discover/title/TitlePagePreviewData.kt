package com.cooper.binge.companion.seerr.ui.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleCollection
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleProvider
import com.cooper.binge.companion.seerr.discover.title.TitleProviders
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.TitleVideo
import com.cooper.binge.companion.seerr.discover.title.VersionState

private const val RUNTIME = 136
private const val BUDGET = 63_000_000L
private const val REVENUE = 467_000_000L
private const val CRITICS = 83
private const val AUDIENCE = 85
private const val IMDB = 8.7
private const val VOTES = 24_000

private fun person(
    id: Int,
    name: String,
    role: String,
) = TitlePerson(id, name, role, null)

private fun related(
    id: Int,
    name: String,
    availability: TitleAvailability,
) = TitleResult(TitleKind.Movie, id, name, "2003", null, null, null, null, availability, null)

/** A film with everything the server gives, standard requestable and 4K held. */
internal fun fullMovie(
    title: String = "The Matrix",
    standard: VersionState = VersionState(TitleAvailability.None, mayRequest = true),
    fourK: VersionState? = VersionState(TitleAvailability.Processing, mayRequest = false),
) = MovieDetails(
    tmdbId = 603,
    title = title,
    tagline = "Free your mind",
    overview = "A hacker learns what his world really is, and what he can do about it.",
    posterUrl = null,
    backdropUrl = null,
    year = "1999",
    runtimeMinutes = RUNTIME,
    certification = "R",
    voteAverage = 8.2,
    voteCount = VOTES,
    genres = listOf("Action", "Science Fiction"),
    keywords = listOf("simulation", "dystopia", "martial arts"),
    status = "Released",
    originalLanguage = "en",
    spokenLanguageCodes = listOf("en", "es"),
    studios = listOf("Warner Bros.", "Village Roadshow Pictures"),
    budget = BUDGET,
    revenue = REVENUE,
    cast = listOf(person(1, "Keanu Reeves", "Neo"), person(2, "Carrie-Anne Moss", "Trinity"), person(3, "Laurence Fishburne", "Morpheus")),
    keyCrew = listOf(person(10, "Lana Wachowski", "Director, Writer"), person(11, "Lilly Wachowski", "Director, Writer")),
    otherCrew = emptyList(),
    videos = listOf(TitleVideo("Official Trailer", "https://www.youtube.com/watch?v=demo", isTrailer = true)),
    providers = TitleProviders("US", null, listOf(TitleProvider(1, "Demo Stream", null)), emptyList()),
    collection = TitleCollection(2344, "The Matrix Collection", null),
    standard = standard,
    fourK = fourK,
    mediaId = 11,
    webUrl = "https://seerr.example/movie/603",
    releaseDate = "1999-03-31",
)

internal fun fullExtras() =
    TitleExtras(
        ratings = TitleRatings(CRITICS, AUDIENCE, IMDB),
        recommendations =
            listOf(
                related(604, "The Matrix Reloaded", TitleAvailability.Available),
                related(605, "The Matrix Revolutions", TitleAvailability.None),
            ),
        similar = listOf(related(606, "Dark City", TitleAvailability.Pending)),
    )

/** A film the server knows almost nothing about. */
internal fun sparseMovie() =
    fullMovie("Untitled").copy(
        tagline = null,
        overview = null,
        runtimeMinutes = null,
        certification = null,
        voteAverage = null,
        genres = emptyList(),
        keywords = emptyList(),
        status = null,
        originalLanguage = null,
        spokenLanguageCodes = emptyList(),
        studios = emptyList(),
        budget = null,
        revenue = null,
        cast = emptyList(),
        keyCrew = emptyList(),
        videos = emptyList(),
        providers = null,
        collection = null,
        releaseDate = null,
    )

internal fun longMovie() =
    fullMovie("The Lord of the Rings: The Fellowship of the Ring (Extended Director's Cut Special Edition)").copy(
        tagline = "One ring to rule them all, one ring to find them, one ring to bring them all and in the darkness bind them",
        studios = listOf("New Line Cinema", "WingNut Films", "The Saul Zaentz Company", "Another Very Long Production Company Name"),
        genres = listOf("Adventure", "Fantasy", "Action", "Epic Historical Drama"),
        cast = listOf(person(1, "Elijah Wood-Montgomery-Fitzgerald", "Frodo Baggins of the Shire, bearer of the one ring")),
    )
