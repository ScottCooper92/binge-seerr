package com.cooper.binge.companion.seerr.discover.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterGroupTest {
    private val everything =
        DiscoverFilters(
            genres = setOf(18),
            genresAny = true,
            keywords = setOf(1),
            excludeKeywords = setOf(2),
            language = "ja",
            releaseFrom = "2020-01-01",
            runtimeMin = 90,
            ratingMin = 7.0,
            voteCountMin = 300,
            providers = setOf(8),
            region = "GB",
            status = setOf(0),
            certificationCountry = "GB",
            certificationLte = "15",
        )

    @Test
    fun `nothing set is no group`() {
        assertEquals(emptyList<FilterGroup>(), DiscoverFilters().setGroups())
    }

    @Test
    fun `every group is set by its own filters and listed in the sheet's order`() {
        assertEquals(FilterGroup.entries, everything.setGroups())
    }

    @Test
    fun `clearing a group clears only it, with the setting that means nothing without it`() {
        FilterGroup.entries.forEach { group ->
            val cleared = everything.cleared(group)
            assertFalse("$group is still set", cleared.isSet(group))
            assertEquals(FilterGroup.entries - group, cleared.setGroups())
        }
        assertFalse(everything.cleared(FilterGroup.Genres).genresAny)
        assertEquals(null, everything.cleared(FilterGroup.Where).region)
        assertEquals(null, everything.cleared(FilterGroup.Certification).certificationCountry)
    }

    @Test
    fun `a region alone is not a filter`() {
        assertTrue(DiscoverFilters(region = "GB").setGroups().isEmpty())
    }

    @Test
    fun `clearing a group keeps what the list opened with in it`() {
        val opened = DiscoverFilters(genres = setOf(28))
        val now = DiscoverFilters(genres = setOf(28, 12), language = "ja")

        assertEquals(setOf(28), now.clearedKeeping(FilterGroup.Genres, opened).genres)
        assertEquals("ja", now.clearedKeeping(FilterGroup.Genres, opened).language)
        assertEquals(null, now.clearedKeeping(FilterGroup.Language, opened).language)
    }

    @Test
    fun `a streaming row's region stays with its service`() {
        val opened = DiscoverFilters(providers = setOf(8), region = "GB")
        val now = DiscoverFilters(providers = setOf(8, 337), region = "GB")

        val cleared = now.clearedKeeping(FilterGroup.Where, opened)

        assertEquals(setOf(8), cleared.providers)
        assertEquals("GB", cleared.region)
    }
}
