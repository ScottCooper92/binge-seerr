package com.cooper.binge.companion.seerr.discover.request

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonSelectionTest {
    private fun season(
        number: Int,
        availability: TitleAvailability = TitleAvailability.None,
    ) = SeasonChoice(number, "Season $number", 8, availability)

    private val mixed =
        listOf(
            season(1, TitleAvailability.Available),
            season(2, TitleAvailability.Pending),
            season(3),
            season(4),
        )

    @Test
    fun `only the open seasons can be picked`() {
        val selection = SeasonSelection(mixed, partialAllowed = true).toggle(1).toggle(2).toggle(3)

        assertEquals(listOf(3), selection.requestSeasons)
    }

    @Test
    fun `nothing is picked at first and so nothing can be sent`() {
        val selection = SeasonSelection(mixed, partialAllowed = true)

        assertTrue(selection.requestSeasons.isEmpty())
        assertFalse(selection.canSubmit)
    }

    @Test
    fun `a picked season can be unpicked`() {
        val selection = SeasonSelection(mixed, partialAllowed = true).toggle(4).toggle(4)

        assertFalse(selection.canSubmit)
    }

    @Test
    fun `all seasons picks the open ones and a second tap clears them`() {
        val all = SeasonSelection(mixed, partialAllowed = true).toggleAll()

        assertEquals(listOf(3, 4), all.requestSeasons)
        assertTrue(all.allOpenPicked)
        assertTrue(all.toggleAll().requestSeasons.isEmpty())
    }

    @Test
    fun `with partial requests off the request is every open season whatever was picked`() {
        val selection = SeasonSelection(mixed, partialAllowed = false).toggle(3)

        assertEquals(listOf(3, 4), selection.requestSeasons)
        assertTrue(selection.canSubmit)
    }

    @Test
    fun `a show with nothing open offers nothing`() {
        val covered =
            SeasonSelection(listOf(season(1, TitleAvailability.Available), season(2, TitleAvailability.Processing)), partialAllowed = false)

        assertTrue(covered.open.isEmpty())
        assertFalse(covered.canSubmit)
        assertFalse(covered.toggleAll().canSubmit)
    }

    @Test
    fun `a season picked before the list changed is dropped once it is covered`() {
        val picked = SeasonSelection(mixed, partialAllowed = true, picked = setOf(3, 4))
        val after = picked.copy(seasons = listOf(season(3, TitleAvailability.Pending), season(4)))

        assertEquals(listOf(4), after.requestSeasons)
    }

    @Test
    fun `the two versions of a season are separate`() {
        val choices = SeasonChoices(standard = listOf(season(1, TitleAvailability.Available)), fourK = listOf(season(1)))

        assertFalse(choices.of(use4k = false).single().isOpen)
        assertTrue(choices.of(use4k = true).single().isOpen)
    }
}
