package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrAddToWatchlistBody
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The watchlist answers from the demo's model: adding and removing change what the details and the list say afterwards. */
class DemoWatchlistTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private fun demo(block: suspend (SeerrApi) -> Unit) = runTest { apis.anonymous(DEMO_ORIGIN, block) }

    @Test
    fun `a film added to the watchlist shows as on it and is listed, and removing it undoes both`() =
        demo { api ->
            val title = DEMO_TITLES.filter { it.isMovie }.last()
            assertFalse(api.movieFullDetails(title.tmdbId).onUserWatchlist == true)

            api.addToWatchlist(SeerrAddToWatchlistBody(title.tmdbId, "movie", title.title))

            assertTrue(api.movieFullDetails(title.tmdbId).onUserWatchlist == true)
            assertTrue(api.discoverWatchlist().results.any { it.id == title.tmdbId })

            api.removeFromWatchlist(title.tmdbId, "movie")

            assertFalse(api.movieFullDetails(title.tmdbId).onUserWatchlist == true)
            assertFalse(api.discoverWatchlist().results.any { it.id == title.tmdbId })
        }

    @Test
    fun `the seeded watchlist is on the list the details agree with`() =
        demo { api ->
            val listed = api.discoverWatchlist().results.mapNotNull { it.id }

            assertTrue(listed.isNotEmpty())
            assertEquals(true, api.movieFullDetails(listed.first()).onUserWatchlist)
        }

    @Test
    fun `the signed-in user's own list carries its size, which follows an add, and another user's is empty`() =
        demo { api ->
            val before = api.userWatchlist(DEMO_USER_ID)
            val added = DEMO_TITLES.filter { it.isMovie }.last()

            api.addToWatchlist(SeerrAddToWatchlistBody(added.tmdbId, "movie", added.title))

            assertEquals(before.results.size, before.totalResults)
            assertEquals(before.results.size + 1, api.userWatchlist(DEMO_USER_ID).totalResults)
            assertTrue(api.userWatchlist(DEMO_USER_ID + 1).results.isEmpty())
        }
}
