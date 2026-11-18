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
    fun `credits come newest first with an undated title last`() {
        val credits =
            SeerrPersonCreditsDto(
                cast =
                    listOf(
                        movie(1, "Old", "1999-03-30"),
                        movie(2, "Undated", ""),
                        movie(3, "New", "2021-12-01"),
                        show(4, "Mid", "2010-04-17"),
                    ),
            ).toPersonCredits()

        assertEquals(listOf("New", "Mid", "Old", "Undated"), credits.cast.map { it.title })
        assertNull(credits.cast.last().date)
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
            ).toPersonDetails()

        assertNull(details.biography)
        assertNull(details.birthday)
        assertNull(details.knownFor)
        assertEquals(listOf("S."), details.alsoKnownAs)
    }
}
