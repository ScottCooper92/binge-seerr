package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrPersonCreditDto
import com.cooper.binge.companion.seerr.seerr.SeerrPersonCreditsDto
import com.cooper.binge.companion.seerr.seerr.SeerrPersonDetailsDto

private const val FIRST_CAST_ID = 9001
private const val CAST_TITLES = 5
private const val CREW_TITLES = 2
private const val FIRST_CREW_ID = 9101
private const val DEMO_GENDER_FEMALE = 1
private const val DEMO_GENDER_MALE = 2
private const val DEMO_POPULARITY = 12.4
private const val DEMO_VOTE_AVERAGE = 7.4

/** The people the demo's title pages list and its search finds, so a tap on any of them opens a page under their own name. Any other id is a made-up person. */
private val DEMO_PEOPLE =
    mapOf(
        9001 to "Alex Sample",
        9002 to "Sam Example",
        9003 to "Jordan Demo",
        9101 to "Riley Director",
        9102 to "Casey Writer",
        9103 to "Morgan Composer",
        SEARCH_PERSON_FIRST to "Marlowe Quinn",
        SEARCH_PERSON_FIRST + 1 to "Ines Valdemar",
        SEARCH_PERSON_FIRST + 2 to "Tobias Hallworth",
    )

/** `person/{id}` and its credits, hand-authored: the credits are titles the demo has, so a tap opens a real demo page. */
internal fun DemoData.personRoutes(): List<DemoRoute> =
    listOf(
        route("GET", "person", "{}") { _, (id) -> ok(demoPerson(id.toInt())) },
        route("GET", "person", "{}", "combined_credits") { _, _ -> ok(demoCredits()) },
    )

private fun demoPerson(id: Int) =
    SeerrPersonDetailsDto(
        id = id,
        name = DEMO_PEOPLE[id] ?: "Demo Person $id",
        biography = "A made-up person, with nothing real behind them. They have appeared in a handful of the demo's titles.",
        birthday = "1980-02-14",
        placeOfBirth = "Demo City",
        knownForDepartment = if (id in FIRST_CREW_ID until SEARCH_PERSON_FIRST) "Directing" else "Acting",
        alsoKnownAs = listOf("The Demo"),
        gender = if (id % 2 == 0) DEMO_GENDER_FEMALE else DEMO_GENDER_MALE,
        popularity = DEMO_POPULARITY,
        homepage = "https://demo.example.com/people/$id",
    )

/** Every demo person is in the same few titles: nothing real is behind any of them, and a tap opens a demo page either way. */
private fun DemoData.demoCredits(): SeerrPersonCreditsDto {
    val movies = DEMO_TITLES.filter { it.isMovie }
    val shows = DEMO_TITLES.filterNot { it.isMovie }
    val cast = (movies.take(CAST_TITLES - 1) + shows.take(1)).map { credit(it, character = "The Hero") }
    // One title listed twice, as TMDB does for a person with two roles in it.
    val twice = movies.take(1).map { credit(it, character = "The Rival") }
    val crew = movies.drop(CAST_TITLES).take(CREW_TITLES).map { credit(it, job = "Director") }
    return SeerrPersonCreditsDto(cast = cast + twice, crew = crew)
}

private fun DemoData.credit(
    title: DemoTitle,
    character: String? = null,
    job: String? = null,
) = SeerrPersonCreditDto(
    id = title.tmdbId,
    mediaType = title.mediaType,
    title = title.title.takeIf { title.isMovie },
    name = title.title.takeUnless { title.isMovie },
    releaseDate = "${title.year}-01-01".takeIf { title.isMovie },
    firstAirDate = "${title.year}-01-01".takeUnless { title.isMovie },
    posterPath = title.poster.ifBlank { null },
    voteAverage = DEMO_VOTE_AVERAGE,
    character = character,
    job = job,
    mediaInfo = SeerrMediaInfoDto(id = title.mediaId, status = mediaStatus(title)),
)
