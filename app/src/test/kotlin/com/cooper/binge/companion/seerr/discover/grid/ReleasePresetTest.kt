package com.cooper.binge.companion.seerr.discover.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReleasePresetTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `each window is the dates it names`() {
        assertEquals(null to null, ReleasePreset.AnyYear.range(today))
        assertEquals("2026-11-19" to null, ReleasePreset.Upcoming.range(today))
        assertEquals("2026-01-01" to "2026-12-31", ReleasePreset.ThisYear.range(today))
        assertEquals("2021-11-18" to "2026-11-18", ReleasePreset.LastFiveYears.range(today))
    }

    @Test
    fun `two dates that are a window are known as it and any other pair is custom`() {
        assertEquals(ReleasePreset.ThisYear, ReleasePreset.of("2026-01-01", "2026-12-31", today))
        assertEquals(ReleasePreset.AnyYear, ReleasePreset.of(null, null, today))
        assertNull(ReleasePreset.of("2020-03-01", null, today))
    }

    @Test
    fun `setting a window replaces both dates and leaves the other filters`() {
        val filters = DiscoverFilters(genres = setOf(18), releaseFrom = "2020-01-01", releaseTo = "2020-12-31")
        val set = filters.withRelease(ReleasePreset.Upcoming, today)
        assertEquals("2026-11-19", set.releaseFrom)
        assertNull(set.releaseTo)
        assertEquals(setOf(18), set.genres)
    }
}
