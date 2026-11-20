package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HubRowSourceTest {
    private fun title(
        kind: TitleKind,
        id: Int,
    ) = TitleResult(kind, id, "t$id", null, null, null, null, null, TitleAvailability.None, null)

    @Test
    fun `a mixed row reads the mixed list and a kind row its own`() {
        assertEquals(TitleSource.Trending, HubRow.Trending(1, null, HubTab.Movies).toSource())
        assertEquals(TitleSource.Watchlist, HubRow.Watchlist(1, null, HubTab.Series).toSource())
        assertEquals(TitleSource.Search("x"), HubRow.Search(1, null, HubTab.Movies, "x").toSource())
        assertEquals(TitleSource.Popular(TitleKind.Series), HubRow.Popular(1, null, HubTab.Series).toSource())
        assertEquals(TitleSource.Upcoming(TitleKind.Movie), HubRow.Upcoming(1, null, HubTab.Movies).toSource())
    }

    @Test
    fun `a category row reads its kind and a company row its fixed kind`() {
        assertEquals(TitleSource.ByGenre(TitleKind.Movie, 28), HubRow.Genre(1, null, HubTab.Movies, 28).toSource())
        assertEquals(TitleSource.ByCompany(TitleKind.Movie, 5), HubRow.Studio(1, null, 5).toSource())
        assertEquals(TitleSource.ByCompany(TitleKind.Series, 6), HubRow.Network(1, null, 6).toSource())
        assertEquals(
            TitleSource.ByKeywords(TitleKind.Series, listOf(1, 2)),
            HubRow.Keywords(1, null, HubTab.Series, listOf(1, 2)).toSource(),
        )
        assertEquals(
            TitleSource.ByStreaming(TitleKind.Movie, "GB", listOf(8)),
            HubRow.Streaming(1, null, HubTab.Movies, "GB", listOf(8)).toSource(),
        )
    }

    @Test
    fun `the tile rows and the media rows are not title lists`() {
        assertNull(HubRow.Genres(1, null, HubTab.Movies).toSource())
        assertNull(HubRow.Studios(1, null).toSource())
        assertNull(HubRow.Networks(1, null).toSource())
        assertNull(HubRow.RecentlyAdded(1, null, HubTab.Movies).toSource())
        assertNull(HubRow.RecentRequests(1, null, HubTab.Movies).toSource())
    }

    @Test
    fun `a hub keeps its own kind and at most ten cards`() {
        val mixed = listOf(title(TitleKind.Person, 0), title(TitleKind.Series, 1)) + (2..20).map { title(TitleKind.Movie, it) }
        val movies = mixed.forHub(HubTab.Movies)
        assertEquals(HUB_ROW_CARDS, movies.size)
        assertEquals(2, movies.first().tmdbId)
        assertEquals(listOf(1), mixed.forHub(HubTab.Series).map { it.tmdbId })
    }
}
