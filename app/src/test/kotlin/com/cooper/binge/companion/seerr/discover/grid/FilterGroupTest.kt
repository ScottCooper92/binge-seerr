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
            certification = "15",
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
}
