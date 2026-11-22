package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.discover.SeerrTitleListRepository
import com.cooper.binge.companion.seerr.discover.sliders.HubHeroLoader
import com.cooper.binge.companion.seerr.discover.sliders.HubMediaSource
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.discover.sliders.SeerrHubScoreSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.util.FakeTitleDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Movies and TV hubs open on a hero in the demo, as they do on a real server: it is the real loader over the demo's own answers. */
class DemoHubHeroTest {
    private val apis = SeerrApiFactory(logRequests = false)

    private class Media : HubMediaSource {
        override suspend fun serverUrl() = "https://demo.seerr.invalid/"

        override suspend fun recentlyAdded() = emptyList<SeerrRequestMediaDto>()

        override suspend fun recentRequests() = emptyList<SeerrRequestMediaDto>()

        override suspend fun hydrate(
            mediaType: String,
            tmdbId: Int,
        ): HydratedTitle? = null
    }

    private fun hero(block: suspend (HubHeroLoader) -> Unit) =
        runBlocking {
            apis.anonymous(DEMO_ORIGIN) { api ->
                val profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = SeerrVersion(3, 2, 0))
                val cache = TitleCache(FakeTitleDao())
                val titles = SeerrTitleListRepository(api = { api }, profile = { profile }, io = Dispatchers.Unconfined, titles = cache)
                val scores = SeerrHubScoreSource(api = { api }, profile = { profile }, io = Dispatchers.Unconfined)
                block(HubHeroLoader(titles, Media(), scores))
            }
        }

    @Test
    fun `the Movies hub has a hero of at least three slides, each with a backdrop`() =
        hero { loader ->
            val slides = loader.load(HubTab.Movies)

            assertTrue("slides: ${slides.size}", slides.size >= MIN_SLIDES)
            assertTrue(slides.all { it.backdropUrl.endsWith(".jpg") })
            assertEquals(slides.size, slides.map { it.tmdbId }.toSet().size)
        }

    @Test
    fun `the TV hub has a hero too`() =
        hero { loader ->
            val slides = loader.load(HubTab.Series)

            assertTrue("slides: ${slides.size}", slides.isNotEmpty())
            assertTrue(slides.all { it.backdropUrl.endsWith(".jpg") })
        }

    @Test
    fun `the hero's scores come back at once, with Rotten Tomatoes and IMDb on a film`() =
        hero { loader ->
            val scored = loader.withScores(loader.load(HubTab.Movies), waitMs = SCORE_WAIT_MS)

            val ratings = scored.first().ratings
            assertTrue("a film has both scores", ratings?.criticsPercent != null && ratings.imdbScore != null)
            assertTrue(scored.all { it.ratings != null })
        }

    private companion object {
        const val MIN_SLIDES = 3

        /** The bound the hub gives the scores read before it shows the hero; the demo answers from memory, so it is never reached. */
        const val SCORE_WAIT_MS = 5_000L
    }
}
