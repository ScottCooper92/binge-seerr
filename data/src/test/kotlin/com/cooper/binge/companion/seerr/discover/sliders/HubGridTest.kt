package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HubGridTest {
    private val movies = HubTab.Movies
    private val series = HubTab.Series

    @Test
    fun `the lists with a preset open the grid on it for the hubs kind`() {
        assertEquals(HubGrid(TitleKind.Movie, GridPreset.Popular), HubRow.Popular(1, null, movies).seeAll())
        assertEquals(HubGrid(TitleKind.Series, GridPreset.Upcoming), HubRow.Upcoming(1, null, series).seeAll())
        assertEquals(HubGrid(TitleKind.Series, GridPreset.Trending), HubRow.Trending(1, null, series).seeAll())
        assertEquals(HubGrid(TitleKind.Movie, GridPreset.Watchlist), HubRow.Watchlist(1, null, movies).seeAll())
    }

    @Test
    fun `a custom genre studio or network slider opens its own category`() {
        assertEquals(HubGrid(TitleKind.Series, GridPreset.Genre(18)), HubRow.Genre(1, null, series, 18).seeAll())
        assertEquals(HubGrid(TitleKind.Movie, GridPreset.Company(420)), HubRow.Studio(1, null, 420).seeAll())
        assertEquals(HubGrid(TitleKind.Series, GridPreset.Company(213)), HubRow.Network(1, null, 213).seeAll())
    }

    @Test
    fun `a keyword or streaming row opens the grid only when it has one id`() {
        assertEquals(HubGrid(TitleKind.Movie, GridPreset.Keyword(9)), HubRow.Keywords(1, null, movies, listOf(9)).seeAll())
        assertNull(HubRow.Keywords(1, null, movies, listOf(9, 10)).seeAll())
        assertEquals(HubGrid(TitleKind.Movie, GridPreset.Provider(8, "GB")), HubRow.Streaming(1, null, movies, "GB", listOf(8)).seeAll())
        assertNull(HubRow.Streaming(1, null, movies, "GB", listOf(8, 9)).seeAll())
    }

    @Test
    fun `the servers own lists and the tile rows have no see all`() {
        assertNull(HubRow.RecentlyAdded(1, null, movies).seeAll())
        assertNull(HubRow.RecentRequests(1, null, movies).seeAll())
        assertNull(HubRow.Search(1, null, movies, "x").seeAll())
        assertNull(HubRow.Genres(1, null, movies).seeAll())
        assertNull(HubRow.Studios(1, null).seeAll())
        assertNull(HubRow.Networks(1, null).seeAll())
    }

    @Test
    fun `a tile opens the grid on its genre or company`() {
        val tile = HubTile(5, "X", null)
        assertEquals(HubGrid(TitleKind.Series, GridPreset.Genre(5)), HubRow.Genres(1, null, series).tileGrid(tile))
        assertEquals(HubGrid(TitleKind.Movie, GridPreset.Company(5)), HubRow.Studios(1, null).tileGrid(tile))
        assertEquals(HubGrid(TitleKind.Series, GridPreset.Company(5)), HubRow.Networks(1, null).tileGrid(tile))
        assertNull(HubRow.Popular(1, null, movies).tileGrid(tile))
    }
}
