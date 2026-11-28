package com.cooper.binge.companion.seerr.discover.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class FilterRangeSlidersTest {
    @Test
    fun `a rating thumb at an end of the track means no bound`() {
        val set = DiscoverFilters().withRatingSliderPosition(6.5f..10f)
        assertEquals(6.5, set.ratingMin)
        assertNull(set.ratingMax)
        assertEquals(6.5f..10f, set.ratingSliderPosition())
        assertNull(set.withRatingSliderPosition(0f..10f).ratingMin)
    }

    @Test
    fun `a rating thumb that did not move keeps its stored bound`() {
        val filters = DiscoverFilters(ratingMin = 6.3, ratingMax = 8.0)
        val moved = filters.withRatingSliderPosition(filters.ratingSliderPosition().start..9f)
        assertEquals(6.3, moved.ratingMin)
        assertEquals(9.0, moved.ratingMax)
    }

    @Test
    fun `a runtime at the top of the track is no maximum`() {
        val set = DiscoverFilters().withRuntimeSliderPosition(60f..RUNTIME_SLIDER_MAX_MINUTES.toFloat())
        assertEquals(60, set.runtimeMin)
        assertNull(set.runtimeMax)
        assertEquals(0f..RUNTIME_SLIDER_MAX_MINUTES.toFloat(), DiscoverFilters(runtimeMin = 0).runtimeSliderPosition())
        assertEquals(60f..90f, DiscoverFilters(runtimeMin = 60, runtimeMax = 90).runtimeSliderPosition())
        assertEquals(240f..240f, DiscoverFilters(runtimeMin = 300).runtimeSliderPosition())
    }

    @Test
    fun `a vote count stop index and its count are inverses`() {
        VoteSliderStops.forEachIndexed { index, votes ->
            assertEquals(index.toFloat(), voteSliderIndex(votes))
            assertEquals(votes, voteAtSliderIndex(index.toFloat()))
        }
        assertEquals(55, voteAtSliderIndex(1.5f))
    }

    @Test
    fun `vote thumbs at the ends of the track mean no bound`() {
        val set = DiscoverFilters().withVoteSliderPosition(2f..VOTE_SLIDER_LAST_INDEX.toFloat())
        assertEquals(100, set.voteCountMin)
        assertNull(set.voteCountMax)
        assertEquals(2f..VOTE_SLIDER_LAST_INDEX.toFloat(), set.voteSliderPosition())
        assertEquals(
            DiscoverFilters(voteCountMin = 10),
            DiscoverFilters(voteCountMin = 10).withVoteSliderPosition(
                1f..VOTE_SLIDER_LAST_INDEX.toFloat(),
            ),
        )
    }

    @Test
    fun `a release start is the first of its month and an end the last`() {
        val filters = DiscoverFilters().withReleaseFromMonth(YearMonth.of(2018, 3)).withReleaseToMonth(YearMonth.of(2020, 2))
        assertEquals("2018-03-01", filters.releaseFrom)
        assertEquals("2020-02-29", filters.releaseTo)
    }

    @Test
    fun `a start after the end swaps the two onto month boundaries`() {
        val filters = DiscoverFilters(releaseTo = "2018-05-31").withReleaseFromMonth(YearMonth.of(2019, 1))
        assertEquals("2018-05-01", filters.releaseFrom)
        assertEquals("2019-01-31", filters.releaseTo)
    }

    @Test
    fun `an end before the start swaps the two onto month boundaries`() {
        val filters = DiscoverFilters(releaseFrom = "2019-04-01").withReleaseToMonth(YearMonth.of(2018, 2))
        assertEquals("2018-02-01", filters.releaseFrom)
        assertEquals("2019-04-30", filters.releaseTo)
    }
}
