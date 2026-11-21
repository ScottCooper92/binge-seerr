package com.cooper.binge.companion.seerr.ui.discover.title.credits

import com.cooper.binge.companion.seerr.discover.title.CreditsDepartment
import com.cooper.binge.companion.seerr.discover.title.TitleCredits
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleCreditsGridTest {
    private fun person(
        id: Int,
        name: String,
        role: String? = null,
    ) = TitlePerson(id, name, role, null)

    @Test
    fun `the cast is one section in the order given, each with their character`() {
        val credits =
            TitleCredits(
                cast = listOf(person(2, "Carrie-Anne Moss", "Trinity"), person(1, "Keanu Reeves", "Neo")),
                departments = emptyList(),
            )

        val grid = creditsGrid(credits, emptyList())

        assertEquals(1, grid.cast.size)
        val section = grid.cast.single()
        assertNull(section.title)
        val people = section.people
        assertEquals(listOf("Carrie-Anne Moss", "Keanu Reeves"), people.map { it.name })
        assertEquals(listOf("Trinity", "Neo"), people.map { it.role })
    }

    @Test
    fun `a cast with nobody in it has no section, so the tab says it is empty`() {
        val grid = creditsGrid(TitleCredits(cast = emptyList(), departments = emptyList()), emptyList())

        assertTrue(grid.cast.isEmpty())
        assertTrue(grid.crew.isEmpty())
    }

    @Test
    fun `the crew is a section per department, titled in the user's language`() {
        val credits =
            TitleCredits(
                cast = emptyList(),
                departments =
                    listOf(
                        CreditsDepartment("Directing", listOf(person(10, "Lana Wachowski", "Director"))),
                        CreditsDepartment("Writing", listOf(person(11, "Lilly Wachowski", "Writer, Screenplay"))),
                    ),
            )

        val grid = creditsGrid(credits, listOf("Dirección", "Guion"))

        assertEquals(listOf("Dirección", "Guion"), grid.crew.map { it.title })
        assertEquals(listOf("Writer, Screenplay"), grid.crew[1].people.map { it.role })
    }

    @Test
    fun `a person in two departments is a card in each, and every card's key opens the same person`() {
        val credits =
            TitleCredits(
                cast = emptyList(),
                departments =
                    listOf(
                        CreditsDepartment("Directing", listOf(person(10, "Lana Wachowski", "Director"))),
                        CreditsDepartment("Writing", listOf(person(10, "Lana Wachowski", "Writer"))),
                    ),
            )

        val grid = creditsGrid(credits, listOf("Directing", "Writing"))
        val keys = grid.crew.flatMap { section -> section.people.map { it.key } }

        assertEquals(2, keys.size)
        assertEquals(2, keys.toSet().size)
        assertEquals(listOf(10, 10), keys.map { grid.personId(it) })
    }

    @Test
    fun `a person listed twice in the cast gets two cards with different keys`() {
        val credits =
            TitleCredits(
                cast = listOf(person(1, "Actor", "Twin one"), person(1, "Actor", "Twin two")),
                departments = emptyList(),
            )

        val grid = creditsGrid(credits, emptyList())
        val people = grid.cast.single().people
        val keys = people.map { it.key }

        assertEquals(2, keys.toSet().size)
        assertEquals(listOf(1, 1), keys.map { grid.personId(it) })
    }

    @Test
    fun `a key nobody issued opens nobody`() {
        assertNull(creditsGrid(TitleCredits(emptyList(), emptyList()), emptyList()).personId("crew/9/9"))
    }
}
