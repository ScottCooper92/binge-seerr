package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.GenreTile
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
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
        rating: Double? = 7.5,
    ) = TitleResult(kind, id, "t$id", "2021", "/p$id.jpg", backdrop, null, rating, TitleAvailability.Available, null)

    private class Titles(
        private val results: List<TitleResult>,
        private val supported: Boolean = true,
        private val fail: Boolean = false,
        private val names: Map<Int, String>? = emptyMap(),
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

        override suspend fun genreNames(kind: TitleKind): Map<Int, String> = names ?: error("no names")
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
    fun `each hero gets its own scores, and a title that has none or fails is left without`() =
        runTest {
            val scores =
                HubScoreSource { _, id ->
                    when (id) {
                        1 -> TitleRatings(97, 91, 7.9)
                        2 -> null
                        else -> error("down")
                    }
                }
            val loader =
                HubHeroLoader(
                    Titles(listOf(title(TitleKind.Movie, 1), title(TitleKind.Movie, 2), title(TitleKind.Movie, 3))),
                    Media(),
                    scores,
                )

            val scored = loader.withScores(loader.load(HubTab.Movies))

            assertEquals(listOf(TitleRatings(97, 91, 7.9), null, null), scored.map { it.ratings })
            assertEquals("the titles and their order are the same", listOf(1, 2, 3), scored.map { it.tmdbId })
        }

    @Test
    fun `a hub with no score source gives its heroes no scores`() =
        runTest {
            val loader = HubHeroLoader(Titles(listOf(title(TitleKind.Movie, 1))), Media())

            assertEquals(listOf(null), loader.withScores(loader.load(HubTab.Movies)).map { it.ratings })
        }

    @Test
    fun `a hero carries its vote average, and none where it is zero or missing`() =
        runTest {
            val titles =
                Titles(
                    listOf(
                        title(TitleKind.Movie, 1, rating = 6.4),
                        title(TitleKind.Movie, 2, rating = 0.0),
                        title(TitleKind.Movie, 3, rating = null),
                    ),
                )
            val hero = HubHeroLoader(titles, Media()).load(HubTab.Movies)
            assertEquals(listOf(6.4f, null, null), hero.map { it.rating })
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
            assertEquals("https://s/movie/4", hero.first().webUrl)
        }

    @Test
    fun `each hero carries its place in the trending list of its kind and its genre names`() =
        runTest {
            val results =
                listOf(
                    title(TitleKind.Series, 1),
                    title(TitleKind.Movie, 2, backdrop = null),
                    title(TitleKind.Movie, 3).copy(genreIds = listOf(28, 99, 12)),
                )
            val hero = HubHeroLoader(Titles(results, names = mapOf(28 to "Action", 12 to "Adventure")), Media()).load(HubTab.Movies)
            // The title without a backdrop still takes rank 1, so the one that follows is #2 as it is in the list.
            assertEquals(listOf(3), hero.map { it.tmdbId })
            assertEquals(2, hero.single().rank)
            assertEquals(listOf("Action", "Adventure"), hero.single().genres)
        }

    @Test
    fun `a server that cannot name genres still has a hero without the genre line`() =
        runTest {
            val results = listOf(title(TitleKind.Movie, 1).copy(genreIds = listOf(28)))
            val hero = HubHeroLoader(Titles(results, names = null), Media()).load(HubTab.Movies)
            assertEquals(listOf(1), hero.map { it.tmdbId })
            assertEquals(emptyList<String>(), hero.single().genres)
            assertEquals(1, hero.single().rank)
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
