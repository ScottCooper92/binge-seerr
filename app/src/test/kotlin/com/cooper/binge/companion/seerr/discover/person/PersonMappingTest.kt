package com.cooper.binge.companion.seerr.discover.person

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrPersonCreditDto
import com.cooper.binge.companion.seerr.seerr.SeerrPersonCreditsDto
import com.cooper.binge.companion.seerr.seerr.SeerrPersonDetailsDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonMappingTest {
    private fun movie(
        id: Int,
        title: String,
        date: String?,
        character: String? = null,
        job: String? = null,
        status: SeerrMediaStatusCode? = null,
    ) = SeerrPersonCreditDto(
        id = id,
        mediaType = "movie",
        title = title,
        releaseDate = date,
        character = character,
        job = job,
        mediaInfo = status?.let { SeerrMediaInfoDto(status = it) },
    )

    private fun show(
        id: Int,
        name: String,
        date: String?,
        character: String? = null,
    ) = SeerrPersonCreditDto(id = id, mediaType = "tv", name = name, firstAirDate = date, character = character)

    @Test
    fun `credits keep the order the server gave them`() {
        val credits =
            SeerrPersonCreditsDto(
                cast =
                    listOf(
                        movie(1, "First", "1999-03-30"),
                        movie(2, "Second", ""),
                        movie(3, "Third", "2021-12-01"),
                        show(4, "Fourth", "2010-04-17"),
                    ),
            ).toPersonCredits()

        assertEquals(listOf("First", "Second", "Third", "Fourth"), credits.cast.map { it.title })
    }

    @Test
    fun `a rating is the vote average, and none where the server gave zero or nothing`() {
        val credits =
            SeerrPersonCreditsDto(
                cast =
                    listOf(
                        movie(1, "Rated", null).copy(voteAverage = 7.5),
                        movie(2, "Zero", null).copy(voteAverage = 0.0),
                        movie(3, "Missing", null),
                    ),
            ).toPersonCredits()

        assertEquals(listOf(7.5f, null, null), credits.cast.map { it.rating })
    }

    @Test
    fun `a title listed once per role reads once with every role`() {
        val credits =
            SeerrPersonCreditsDto(
                cast = listOf(movie(1, "Twin", "2000-01-01", character = "Elder"), movie(1, "Twin", "2000-01-01", character = "Younger")),
                crew = listOf(movie(2, "Made", "2001-01-01", job = "Director"), movie(2, "Made", "2001-01-01", job = "Writer")),
            ).toPersonCredits()

        assertEquals("Elder, Younger", credits.cast.single().role)
        assertEquals("Director, Writer", credits.crew.single().role)
        assertEquals(credits.cast.map { it.key }.distinct(), credits.cast.map { it.key })
    }

    @Test
    fun `a film and a show that share an id are two credits with two keys`() {
        val credits = SeerrPersonCreditsDto(cast = listOf(movie(7, "Film", "2000-01-01"), show(7, "Show", "2001-01-01"))).toPersonCredits()

        assertEquals(2, credits.cast.size)
        assertEquals(
            2,
            credits.cast
                .map { it.key }
                .distinct()
                .size,
        )
        assertEquals(setOf(TitleKind.Movie, TitleKind.Series), credits.cast.map { it.kind }.toSet())
    }

    @Test
    fun `a credit of an unknown media type is left out`() {
        val credits =
            SeerrPersonCreditsDto(
                cast = listOf(movie(1, "Film", "2000-01-01"), SeerrPersonCreditDto(id = 2, mediaType = "person")),
            ).toPersonCredits()

        assertEquals(listOf("Film"), credits.cast.map { it.title })
    }

    @Test
    fun `the status the server holds is on the credit and a blank role is none`() {
        val credits =
            SeerrPersonCreditsDto(
                cast = listOf(movie(1, "Held", "2000-01-01", character = " ", status = SeerrMediaStatusCode.Available)),
            ).toPersonCredits()

        assertEquals(TitleAvailability.Available, credits.cast.single().availability)
        assertNull(credits.cast.single().role)
    }

    @Test
    fun `a person with nothing gives empty lists and a profile with blanks left out`() {
        assertTrue(SeerrPersonCreditsDto().toPersonCredits().cast.isEmpty())

        val details =
            SeerrPersonDetailsDto(
                id = 5,
                name = "Sam",
                biography = "",
                birthday = "",
                knownForDepartment = " ",
                alsoKnownAs = listOf("", "S."),
            ).toPersonDetails("u")

        assertNull(details.biography)
        assertNull(details.birthday)
        assertNull(details.knownFor)
        assertEquals(listOf("S."), details.alsoKnownAs)
    }

    @Test
    fun `the gender code, links and popularity map and the share address is the server's page`() {
        val details =
            SeerrPersonDetailsDto(
                id = 5,
                name = "Sam",
                gender = 3,
                popularity = 0.0,
                imdbId = "nm123",
                homepage = "https://sam.example",
            ).toPersonDetails("https://seerr.example/person/5")

        assertEquals(PersonGender.NonBinary, details.gender)
        assertNull(details.popularity)
        assertEquals("nm123", details.imdbId)
        assertEquals("https://seerr.example/person/5", details.webUrl)
        assertNull(SeerrPersonDetailsDto(id = 6, gender = 0).toPersonDetails("u").gender)
        assertEquals(PersonGender.Female, SeerrPersonDetailsDto(id = 6, gender = 1).toPersonDetails("u").gender)
        assertEquals(PersonGender.Male, SeerrPersonDetailsDto(id = 6, gender = 2).toPersonDetails("u").gender)
    }

    @Test
    fun `age at death comes from the two dates and is none where either is missing or backwards`() {
        fun age(
            born: String?,
            died: String?,
        ) = SeerrPersonDetailsDto(id = 1, birthday = born, deathday = died).toPersonDetails("u").ageAtDeath

        assertEquals(70, age("1950-06-15", "2021-06-14"))
        assertEquals(72, age("1950-06-15", "2022-06-15"))
        assertNull(age("1950-06-15", null))
        assertNull(age(null, "2022-06-15"))
        assertNull(age("2022-06-15", "1950-06-15"))
        assertNull(age("not a date", "2022-06-15"))
    }
}
