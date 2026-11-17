package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.demo.DEMO_HOST
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDiscoverTest {
    private val factory = SeerrApiFactory(logRequests = false)

    private fun <T> demo(block: suspend (SeerrApi) -> T): T = runBlocking { factory.anonymous("https://$DEMO_HOST/", block) }

    @Test
    fun `every discover call is answered with titles`() {
        val pages =
            demo { api ->
                listOf(
                    api.discoverTrending(),
                    api.discoverMovies(),
                    api.discoverTv(),
                    api.discoverUpcomingMovies(),
                    api.discoverUpcomingTv(),
                    api.discoverMoviesByGenre(28),
                    api.discoverTvByGenre(18),
                    api.discoverMoviesByStudio(1),
                    api.discoverTvByNetwork(1),
                    api.discoverMoviesByLanguage("en"),
                    api.discoverTvByLanguage("en"),
                    api.discoverMoviesByKeyword(1),
                    api.discoverWatchlist(),
                )
            }
        pages.forEach { assertTrue(it.results.isNotEmpty()) }
    }

    @Test
    fun `every row carries its media type as the servers send it`() {
        demo { api ->
            assertTrue(api.discoverMovies().results.all { it.mediaType == "movie" })
            assertTrue(api.discoverTv().results.all { it.mediaType == "tv" })
            assertEquals(
                setOf("movie", "tv"),
                api
                    .discoverTrending()
                    .results
                    .mapNotNull { it.mediaType }
                    .toSet(),
            )
        }
    }

    @Test
    fun `a page beyond the last is empty and the counts hold`() {
        demo { api ->
            val first = api.discoverTrending(page = 1)
            assertEquals(1, first.page)
            val beyond = api.discoverTrending(page = first.totalPages + 1)
            assertTrue(beyond.results.isEmpty())
            assertEquals(first.totalResults, beyond.totalResults)
        }
    }

    @Test
    fun `search finds a title by part of its name and nothing for no query`() {
        demo { api ->
            assertEquals(listOf("Big Buck Bunny"), api.searchTitles("buck bunny").results.map { it.title })
            assertTrue(api.searchTitles("").results.isEmpty())
            assertTrue(api.searchTitles("zzzz").results.isEmpty())
        }
    }

    @Test
    fun `a title carries the server id and a status the repository can map`() {
        val rows = demo { it.discoverMovies().results }
        val mapped = rows.mapNotNull { it.toTitleResult(TitleKind.Movie) }
        assertEquals(rows.size, mapped.size)
        assertTrue(mapped.all { it.mediaId != null && it.artworkPath != null })
        assertFalse(mapped.isEmpty())
    }

    @Test
    fun `the genre rows have a backdrop each`() {
        demo { api ->
            assertTrue(api.movieGenreSlider().all { it.backdrops.isNotEmpty() })
            assertTrue(api.tvGenreSlider().isNotEmpty())
        }
    }
}
