package com.cooper.binge.companion.seerr.ui.search.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A result's status line carries one genre, so the line stays on one line (#2596). */
class TitleSearchPrimaryGenreTest {
    private val names = mapOf(28 to "Action", 878 to "Science Fiction", 18 to "Drama")

    private fun result(vararg genreIds: Int) =
        TitleResult(
            kind = TitleKind.Movie,
            tmdbId = 27205,
            name = "Inception",
            year = "2010",
            artworkPath = null,
            backdropPath = null,
            overview = null,
            rating = 8.4,
            availability = TitleAvailability.None,
            mediaId = null,
            genreIds = genreIds.toList(),
        )

    @Test
    fun `a result with two genres shows only the first`() {
        assertEquals("Action", result(28, 878).primaryGenre(names))
    }

    @Test
    fun `an unnamed first genre falls to the next one the server names`() {
        assertEquals("Drama", result(9999, 18).primaryGenre(names))
    }

    @Test
    fun `a result with no named genre shows none`() {
        assertNull(result().primaryGenre(names))
        assertNull(result(9999).primaryGenre(names))
    }
}
