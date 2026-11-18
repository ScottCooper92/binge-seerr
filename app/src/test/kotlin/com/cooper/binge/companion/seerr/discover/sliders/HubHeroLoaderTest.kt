package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.GenreTile
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HubHeroLoaderTest {
    private fun title(
        kind: TitleKind,
        id: Int,
        backdrop: String? = "/b$id.jpg",
    ) = TitleResult(kind, id, "t$id", "2021", "/p$id.jpg", backdrop, null, 7.5, TitleAvailability.Available, null)

    private class Titles(
        private val results: List<TitleResult>,
        private val supported: Boolean = true,
        private val fail: Boolean = false,
    ) : TitleListRepository {
        var loaded: TitleSource? = null

        override suspend fun supports(source: TitleSource) = supported

        override suspend fun load(
            source: TitleSource,
            page: Int,
        ): TitlePage {
            loaded = source
            if (fail) error("down")
            return TitlePage(results, 1, 1)
        }

        override suspend fun genres(kind: TitleKind): List<GenreTile> = emptyList()
    }

    private class Media : HubMediaSource {
        override suspend fun serverUrl() = "https://s/"

        override suspend fun recentlyAdded() = emptyList<SeerrRequestMediaDto>()

        override suspend fun recentRequests() = emptyList<SeerrRequestMediaDto>()

        override suspend fun hydrate(
            mediaType: String,
            tmdbId: Int,
        ): HydratedTitle? = null
    }

    @Test
    fun `the hero is the first five trending titles of the hubs kind that have a backdrop`() =
        runTest {
            val results =
                listOf(title(TitleKind.Series, 1), title(TitleKind.Movie, 2, backdrop = null), title(TitleKind.Person, 3)) +
                    (4..12).map { title(TitleKind.Movie, it) }
            val titles = Titles(results)
            val hero = HubHeroLoader(titles, Media()).load(HubTab.Movies)
            assertEquals(listOf(4, 5, 6, 7, 8), hero.map { it.tmdbId })
            assertEquals(TitleSource.Trending, titles.loaded)
            assertTrue(hero.first().backdropUrl.endsWith("/b4.jpg"))
            assertEquals(7.5f, hero.first().rating)
            assertEquals("https://s/movie/4", hero.first().webUrl)
        }

    @Test
    fun `a server without the list or with nothing trending has no hero and a failure is the callers`() =
        runTest {
            assertTrue(HubHeroLoader(Titles(listOf(title(TitleKind.Movie, 1)), supported = false), Media()).load(HubTab.Movies).isEmpty())
            assertTrue(HubHeroLoader(Titles(emptyList()), Media()).load(HubTab.Movies).isEmpty())
            assertThrows(IllegalStateException::class.java) {
                runTest { HubHeroLoader(Titles(emptyList(), fail = true), Media()).load(HubTab.Series) }
            }
        }
}
