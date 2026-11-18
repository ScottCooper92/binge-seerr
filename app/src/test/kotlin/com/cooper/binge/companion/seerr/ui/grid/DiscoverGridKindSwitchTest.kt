package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoverGridKindSwitchTest {
    private fun switched(
        preset: GridPreset,
        title: String?,
        kind: TitleKind = TitleKind.Series,
    ) = DiscoverGridRoute(
        TitleKind.Movie,
        preset,
        title,
    ).carriedTo(kind, DiscoverSort.ReleaseNewest, DiscoverFilters(genres = setOf(18), runtimeMin = 90))

    @Test
    fun `a title that names a kind is dropped, so the new grid is called by its own kind`() {
        assertNull(switched(GridPreset.Popular, "Popular movies").title)
        assertNull(switched(GridPreset.Upcoming, "Upcoming movies").title)
        assertNull(switched(GridPreset.Genre(18), "Drama").title)
    }

    @Test
    fun `a title for something neither kind owns stays`() {
        assertEquals("Japanese", switched(GridPreset.Language("ja"), "Japanese").title)
        assertEquals("Max", switched(GridPreset.Provider(384, "GB"), "Max").title)
        assertEquals("Heist", switched(GridPreset.Keyword(9), "Heist").title)
    }

    @Test
    fun `the new route is the other kind with the carried preset, filters and order`() {
        val route = switched(GridPreset.Genre(18), "Drama")
        assertEquals(TitleKind.Series, route.kind)
        assertEquals(GridPreset.Popular, route.preset)
        assertEquals(DiscoverSort.FirstAirNewest, route.sort)
        assertEquals(DiscoverFilters(runtimeMin = 90), route.filters)
    }
}
