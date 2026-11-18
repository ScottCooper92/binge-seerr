package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.seerr.SeerrCastDto
import com.cooper.binge.companion.seerr.seerr.SeerrCreditsDto
import com.cooper.binge.companion.seerr.seerr.SeerrCrewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleCreditsMappingTest {
    private fun crew(
        id: Int,
        name: String,
        job: String?,
        department: String?,
    ) = SeerrCrewDto(id, name, job, department, null)

    @Test
    fun `the cast is in billing order and a character is blank-safe`() {
        val credits =
            SeerrCreditsDto(
                cast =
                    listOf(
                        SeerrCastDto(2, "B", "Trinity", null, 1),
                        SeerrCastDto(1, "A", " ", null, 0),
                        SeerrCastDto(3, "C", "Cypher", null, null),
                    ),
            ).let { it.toTitleCredits() }

        assertEquals(listOf("A", "B", "C"), credits.cast.map { it.name })
        assertEquals(listOf(null, "Trinity", "Cypher"), credits.cast.map { it.role })
    }

    @Test
    fun `the crew is grouped by department in the listed order, the rest alphabetical`() {
        val credits =
            SeerrCreditsDto(
                crew =
                    listOf(
                        crew(1, "Cam", "Cinematography", "Camera"),
                        crew(2, "Vfx", "Supervisor", "Visual Effects"),
                        crew(3, "Dir", "Director", "Directing"),
                        crew(4, "Lit", "Gaffer", "Lighting"),
                        crew(5, "Wri", "Writer", "Writing"),
                    ),
            ).toTitleCredits()

        assertEquals(listOf("Directing", "Writing", "Camera", "Lighting", "Visual Effects"), credits.departments.map { it.name })
    }

    @Test
    fun `a person with two jobs in one department is one row under both`() {
        val credits =
            SeerrCreditsDto(
                crew =
                    listOf(
                        crew(5, "Wri", "Writer", "Writing"),
                        crew(5, "Wri", "Screenplay", "Writing"),
                        crew(5, "Wri", "Writer", "Writing"),
                    ),
            ).toTitleCredits()

        val writing = credits.departments.single().people
        assertEquals(1, writing.size)
        assertEquals("Writer, Screenplay", writing.single().role)
    }

    @Test
    fun `a person in two departments is in both and a crew with no department is grouped apart`() {
        val credits =
            SeerrCreditsDto(
                crew =
                    listOf(
                        crew(7, "Both", "Director", "Directing"),
                        crew(7, "Both", "Producer", "Production"),
                        crew(8, "Nobody", "Thanks", null),
                    ),
            ).toTitleCredits()

        assertEquals(listOf("Directing", "Production", ""), credits.departments.map { it.name })
        assertEquals(
            listOf(7),
            credits.departments
                .first()
                .people
                .map { it.personId },
        )
    }

    @Test
    fun `a title with no credits has neither cast nor departments`() {
        val credits = (null as SeerrCreditsDto?).toTitleCredits()

        assertTrue(credits.cast.isEmpty())
        assertTrue(credits.departments.isEmpty())
    }

    @Test
    fun `a person listed twice in the cast reads once under both characters`() {
        val credits =
            SeerrCreditsDto(
                cast =
                    listOf(
                        SeerrCastDto(7, "Twin", "Elder", null, 0),
                        SeerrCastDto(8, "Other", "Guard", null, 1),
                        SeerrCastDto(7, "Twin", "Younger", null, 2),
                    ),
            ).toTitleCredits()

        assertEquals(listOf(7, 8), credits.cast.map { it.personId })
        assertEquals("Elder, Younger", credits.cast.first().role)
        assertEquals(credits.cast.map { it.personId }.distinct(), credits.cast.map { it.personId })
    }
}
