package com.cooper.binge.companion.seerr.ui.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.RATING_SORT_VOTE_FLOOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoverFilterDraftTest {
    private val popular = DiscoverFilterDraft(TitleKind.Movie, DiscoverSort.PopularityDesc, DiscoverFilters(genres = setOf(18)))

    @Test
    fun `moving to an order by rating sets the vote floor and keeps the other filters`() {
        val rated = popular.withSort(DiscoverSort.RatingDesc)
        assertEquals(RATING_SORT_VOTE_FLOOR, rated.filters.voteCountMin)
        assertEquals(setOf(18), rated.filters.genres)
    }

    @Test
    fun `a floor the user cleared stays cleared while the order stays by rating`() {
        val cleared = popular.withSort(DiscoverSort.RatingDesc).let { it.copy(filters = it.filters.copy(voteCountMin = null)) }
        assertNull(cleared.withSort(DiscoverSort.RatingAsc).filters.voteCountMin)
    }

    @Test
    fun `an order that is not by rating sets no floor`() {
        assertNull(popular.withSort(DiscoverSort.VotesDesc).filters.voteCountMin)
    }
}
