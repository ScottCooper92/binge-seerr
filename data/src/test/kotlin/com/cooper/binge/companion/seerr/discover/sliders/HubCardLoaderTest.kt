package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.GenreTile
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitlePage
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubCardLoaderTest {
    private fun result(
        kind: TitleKind,
        id: Int,
    ) = TitleResult(kind, id, "t$id", "2020", "/p$id.jpg", null, null, null, TitleAvailability.Available, null)

    @Test
    fun `a title's score becomes the card's rating and an unrated title has none`() {
        val rated = result(TitleKind.Movie, 1).copy(rating = 7.74)
        val unrated = result(TitleKind.Movie, 2).copy(rating = 0.0)
        val unknown = result(TitleKind.Movie, 3).copy(rating = null)
        assertEquals(7.74f, rated.toCard("https://s/").rating)
        assertEquals(null, unrated.toCard("https://s/").rating)
        assertEquals(null, unknown.toCard("https://s/").rating)
    }

    private class FakeTitles(
        private val results: List<TitleResult>,
        private val supported: Boolean = true,
    ) : TitleListRepository {
        var loaded: TitleSource? = null

        override suspend fun supports(source: TitleSource) = supported

        override suspend fun load(
            source: TitleSource,
            page: Int,
        ): TitlePage {
            loaded = source
            return TitlePage(results, 1, 1)
        }

        override suspend fun genres(kind: TitleKind): List<GenreTile> = emptyList()
    }

    private class FakeMedia(
        val added: List<SeerrRequestMediaDto> = emptyList(),
        val requested: List<SeerrRequestMediaDto> = emptyList(),
        val missing: Set<Int> = emptySet(),
    ) : HubMediaSource {
        override suspend fun serverUrl() = "https://s/"

        override suspend fun recentlyAdded() = added

        override suspend fun recentRequests() = requested

        override suspend fun hydrate(
            mediaType: String,
            tmdbId: Int,
        ) = if (tmdbId in missing) null else HydratedTitle("n$tmdbId", "https://img/$tmdbId", "2021")
    }

    private fun ref(
        id: Int,
        type: String,
    ) = SeerrRequestMediaDto(tmdbId = id, mediaType = type)

    @Test
    fun `a title row keeps the hub's kind and builds a poster url`() =
        runTest {
            val titles = FakeTitles(listOf(result(TitleKind.Movie, 1), result(TitleKind.Series, 2)))
            val cards = HubCardLoader(titles, FakeMedia()).load(HubRow.Trending(1, null, HubTab.Movies))
            assertEquals(listOf(1), cards.map { it.tmdbId })
            assertTrue(
                cards
                    .single()
                    .artworkUrl
                    .orEmpty()
                    .endsWith("/p1.jpg"),
            )
            assertEquals("https://s/movie/1", cards.single().webUrl)
            assertEquals(TitleSource.Trending, titles.loaded)
        }

    @Test
    fun `a row the server lacks has no cards and asks for nothing`() =
        runTest {
            val titles = FakeTitles(listOf(result(TitleKind.Movie, 1)), supported = false)
            assertTrue(HubCardLoader(titles, FakeMedia()).load(HubRow.Trending(1, null, HubTab.Movies)).isEmpty())
            assertEquals(null, titles.loaded)
        }

    @Test
    fun `recently added keeps one kind once each and drops a title that did not hydrate`() =
        runTest {
            val media =
                FakeMedia(
                    added = listOf(ref(1, "movie"), ref(1, "movie"), ref(2, "tv"), ref(3, "movie"), ref(4, "movie")),
                    missing = setOf(3),
                )
            val cards = HubCardLoader(FakeTitles(emptyList()), media).load(HubRow.RecentlyAdded(1, null, HubTab.Movies))
            assertEquals(listOf(1, 4), cards.map { it.tmdbId })
            assertEquals("n1", cards.first().name)
            assertEquals("https://s/movie/4", cards.last().webUrl)
        }

    @Test
    fun `recent requests read the request list for the series hub`() =
        runTest {
            val media = FakeMedia(requested = listOf(ref(1, "movie"), ref(2, "tv")))
            val cards = HubCardLoader(FakeTitles(emptyList()), media).load(HubRow.RecentRequests(1, null, HubTab.Series))
            assertEquals(listOf(2), cards.map { it.tmdbId })
            assertEquals(TitleKind.Series, cards.single().kind)
        }

    @Test
    fun `a tile row has no cards`() =
        runTest {
            assertTrue(HubCardLoader(FakeTitles(emptyList()), FakeMedia()).load(HubRow.Genres(1, null, HubTab.Movies)).isEmpty())
        }

    @Test
    fun `a title that is only in 4K shows its 4K status on a recently added card`() =
        runTest {
            val only4k = SeerrRequestMediaDto(tmdbId = 1, mediaType = "movie", status4k = SeerrMediaStatusCode.Available)
            val media = FakeMedia(added = listOf(only4k))
            val cards = HubCardLoader(FakeTitles(emptyList()), media).load(HubRow.RecentlyAdded(1, null, HubTab.Movies))
            assertEquals(TitleAvailability.Available, cards.single().availability)
        }
}
