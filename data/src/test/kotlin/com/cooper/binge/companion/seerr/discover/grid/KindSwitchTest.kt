package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KindSwitchTest {
    @Test
    fun `a kind's own ids go and what a title is stays`() {
        val carried =
            DiscoverFilters(
                genres = setOf(18),
                company = 420,
                status = setOf(1),
                certificationLte = "PG",
                certificationCountry = "US",
                keywords = setOf(9),
                language = "ja",
                releaseFrom = "2020-01-01",
                runtimeMin = 90,
                ratingMin = 7.0,
                voteCountMin = 300,
                providers = setOf(8),
                region = "GB",
            ).carriedToOtherKind()
        assertTrue(carried.genres.isEmpty())
        assertNull(carried.company)
        assertTrue(carried.status.isEmpty())
        assertNull(carried.certificationLte)
        assertEquals(setOf(9), carried.keywords)
        assertEquals("ja", carried.language)
        assertEquals("2020-01-01", carried.releaseFrom)
        assertEquals(90, carried.runtimeMin)
        assertEquals(setOf(8), carried.providers)
        assertEquals("GB", carried.region)
    }

    @Test
    fun `a preset that names one kind's id falls back to popular and the rest stay`() {
        assertEquals(GridPreset.Popular, GridPreset.Genre(18).carriedToOtherKind())
        assertEquals(GridPreset.Popular, GridPreset.Company(420).carriedToOtherKind())
        assertEquals(GridPreset.Upcoming, GridPreset.Upcoming.carriedToOtherKind())
        assertEquals(GridPreset.Language("ja"), GridPreset.Language("ja").carriedToOtherKind())
        assertEquals(GridPreset.Provider(8, "GB"), GridPreset.Provider(8, "GB").carriedToOtherKind())
    }

    @Test
    fun `an order keeps its meaning in the other kind`() {
        assertEquals(DiscoverSort.RatingDesc, DiscoverSort.RatingDesc.carriedTo(TitleKind.Series))
        assertEquals(DiscoverSort.FirstAirNewest, DiscoverSort.ReleaseNewest.carriedTo(TitleKind.Series))
        assertEquals(DiscoverSort.TitleAsc, DiscoverSort.NameAsc.carriedTo(TitleKind.Movie))
        assertEquals(DiscoverSort.PopularityDesc, DiscoverSort.RevenueDesc.carriedTo(TitleKind.Series))
    }
}
