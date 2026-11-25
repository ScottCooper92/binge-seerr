package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.discover.sliders.toCard
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.FakeTitleDao
import com.cooper.homelab.core.foundation.ServerVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private val JSON = headersOf("Content-Type", "application/json")

/** A mixed page as `discover/trending` and `search` send it: a movie, a show, a person, a collection and a kind the app lacks. */
private const val MIXED_PAGE = """{"page":1,"totalPages":3,"totalResults":57,"results":[
{"id":603,"mediaType":"movie","title":"The Matrix","posterPath":"/m.jpg","backdropPath":"/mb.jpg","overview":"A hacker.","releaseDate":"1999-03-30","voteAverage":8.2,
 "mediaInfo":{"id":11,"tmdbId":603,"status":5,"requests":[]}},
{"id":1399,"mediaType":"tv","name":"Game of Thrones","posterPath":"/g.jpg","firstAirDate":"2011-04-17","voteAverage":0.0,
 "mediaInfo":{"id":12,"status":3}},
{"id":6384,"mediaType":"person","name":"Keanu Reeves","profilePath":"/k.jpg","knownForDepartment":"Acting"},
{"id":2344,"mediaType":"collection","title":"The Matrix Collection","posterPath":"/c.jpg"},
{"id":9,"mediaType":"company","name":"A studio"}]}"""

/** A row with no media type, which no server sends, still takes its kind from the source that asked for it. */
private const val MOVIES_PAGE = """{"page":2,"totalPages":2,"totalResults":21,"results":[
{"id":27205,"title":"Inception","posterPath":"/i.jpg","releaseDate":"2010-07-15","voteAverage":8.4,"genreIds":[28,878],"originalLanguage":"en"}]}"""

/**
 * The watchlist carries no artwork. A Plex user's rows repeat the TMDB id as `id`; a user with no Plex account gets the server's
 * own rows, where `id` is the row's number.
 */
private const val WATCHLIST_PAGE = """{"page":1,"totalPages":1,"totalResults":2,"results":[
{"id":438631,"ratingKey":"abc","title":"Dune","mediaType":"movie","tmdbId":438631},
{"id":7,"ratingKey":"def","title":"Severance","mediaType":"tv","tmdbId":95396}]}"""

/** What `movie/{id}` and `tv/{id}` answer for a title: the same lookup Recently added is hydrated by. */
private const val LOOKUP = """{"title":"Looked up","posterPath":"/looked-up.jpg","releaseDate":"2021-10-22"}"""

private const val GENRE_SLIDER = """[{"id":28,"name":"Action","backdrops":["/a.jpg","/b.jpg"]},{"id":35,"name":"Comedy","backdrops":[]}]"""

class TitleListRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = ServerVersion.of(3, 2, 0))

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun answer(body: String) {
        seerr.dispatcher = { FakeResponse(headers = JSON, body = body) }
    }

    private fun <T> withRepository(block: suspend (TitleListRepository) -> T): T =
        runBlocking {
            factory.anonymous(seerr.url()) { api ->
                val titles = TitleCache(FakeTitleDao())
                block(SeerrTitleListRepository(api = { api }, profile = { profile }, io = Dispatchers.Unconfined, titles = titles))
            }
        }

    private fun lastCall() = seerr.requests.last().url

    @Test
    fun `trending maps a movie a show a person and a collection and leaves out a kind it lacks`() {
        answer(MIXED_PAGE)
        val page = withRepository { it.load(TitleSource.Trending, page = 1) }

        assertEquals("/api/v1/discover/trending", lastCall().encodedPath)
        assertEquals("1", lastCall().queryParameter("page"))
        assertEquals(listOf(TitleKind.Movie, TitleKind.Series, TitleKind.Person, TitleKind.Collection), page.results.map { it.kind })
        assertTrue(page.hasMore)
        assertEquals(3, page.totalPages)
        val matrix = page.results.first()
        assertEquals("The Matrix", matrix.name)
        assertEquals("1999", matrix.year)
        assertEquals(8.2, matrix.rating ?: 0.0, 0.0)
        assertEquals(TitleAvailability.Available, matrix.availability)
        assertEquals(11, matrix.mediaId)
        assertEquals("/m.jpg", matrix.artworkPath)
        assertEquals(TitleAvailability.Processing, page.results[1].availability)
        assertNull("a zero vote is no rating", page.results[1].rating)
        assertEquals("a person's artwork is the profile photo", "/k.jpg", page.results[2].artworkPath)
        assertEquals(TitleAvailability.None, page.results[2].availability)
    }

    @Test
    fun `a list of one kind takes its kind from the source`() {
        answer(MOVIES_PAGE)
        val page = withRepository { it.load(TitleSource.Popular(TitleKind.Movie), page = 2) }

        assertEquals("/api/v1/discover/movies", lastCall().encodedPath)
        assertEquals("2", lastCall().queryParameter("page"))
        assertFalse(page.hasMore)
        assertEquals(TitleKind.Movie, page.results.single().kind)
        assertEquals(27205, page.results.single().tmdbId)
    }

    @Test
    fun `every source asks for its own path`() {
        answer(MOVIES_PAGE)
        val paths =
            listOf(
                TitleSource.Popular(TitleKind.Series) to "/api/v1/discover/tv",
                TitleSource.Upcoming(TitleKind.Movie) to "/api/v1/discover/movies/upcoming",
                TitleSource.Upcoming(TitleKind.Series) to "/api/v1/discover/tv/upcoming",
                TitleSource.ByGenre(TitleKind.Movie, 28) to "/api/v1/discover/movies/genre/28",
                TitleSource.ByGenre(TitleKind.Series, 18) to "/api/v1/discover/tv/genre/18",
                TitleSource.ByCompany(TitleKind.Movie, 420) to "/api/v1/discover/movies/studio/420",
                TitleSource.ByCompany(TitleKind.Series, 213) to "/api/v1/discover/tv/network/213",
                TitleSource.ByLanguage(TitleKind.Movie, "ja") to "/api/v1/discover/movies/language/ja",
                TitleSource.ByLanguage(TitleKind.Series, "ko") to "/api/v1/discover/tv/language/ko",
                TitleSource.ByKeyword(TitleKind.Movie, 9715) to "/api/v1/discover/keyword/9715/movies",
                TitleSource.ByKeyword(TitleKind.Series, 9715) to "/api/v1/discover/tv",
                TitleSource.Watchlist to "/api/v1/discover/watchlist",
            )
        paths.forEach { (source, path) ->
            withRepository { it.load(source, page = 1) }
            assertEquals(source.toString(), path, lastCall().encodedPath)
        }
        val showKeyword = seerr.requests.first { it.url.encodedPath == "/api/v1/discover/tv" && it.url.queryParameter("keywords") != null }
        assertEquals("a show's keyword is a query on the base call", "9715", showKeyword.url.queryParameter("keywords"))
    }

    @Test
    fun `a popular list asks for the popularity order on the base call, as the grid does`() {
        answer(MOVIES_PAGE)
        withRepository { it.load(TitleSource.Popular(TitleKind.Movie), page = 1) }
        assertEquals("/api/v1/discover/movies", lastCall().encodedPath)
        assertEquals("popularity.desc", lastCall().queryParameter("sortBy"))
    }

    @Test
    fun `a search sends the query encoded and maps people next to titles`() {
        answer(MIXED_PAGE)
        val page = withRepository { it.load(TitleSource.Search("the matrix & more"), page = 1) }

        assertEquals("/api/v1/search", lastCall().encodedPath)
        assertEquals("the matrix & more", lastCall().queryParameter("query"))
        assertTrue("a space is %20 and not +", lastCall().encodedQuery.orEmpty().contains("query=the%20matrix%20%26%20more"))
        assertEquals(4, page.results.size)
    }

    /** The watchlist route answers [WATCHLIST_PAGE]; a title's lookup answers with a poster, or fails where [lookups] is false. */
    private fun answerWatchlist(lookups: Boolean = true) {
        seerr.dispatcher = { request ->
            when {
                request.url.encodedPath.endsWith("/discover/watchlist") -> FakeResponse(headers = JSON, body = WATCHLIST_PAGE)
                lookups -> FakeResponse(headers = JSON, body = LOOKUP)
                else -> FakeResponse(code = 500)
            }
        }
    }

    @Test
    fun `the watchlist names a title by its tmdb id and not by a row number`() {
        answerWatchlist()
        val titles = withRepository { it.load(TitleSource.Watchlist, page = 1) }.results

        assertEquals(listOf(438631, 95396), titles.map { it.tmdbId })
        assertEquals(listOf(TitleKind.Movie, TitleKind.Series), titles.map { it.kind })
    }

    @Test
    fun `a watchlist row the server sent with no poster takes the poster and year of the title cache`() {
        answerWatchlist()
        val titles = withRepository { it.load(TitleSource.Watchlist, page = 1) }.results

        assertEquals(listOf("/looked-up.jpg", "/looked-up.jpg"), titles.map { it.artworkPath })
        assertEquals(listOf("2021", "2021"), titles.map { it.year })
        // The URL a Recently added card is hydrated to for the same title, so a title in both rows draws one poster.
        assertEquals("https://image.tmdb.org/t/p/w342/looked-up.jpg", titles.first().toCard("https://s/").artworkUrl)
    }

    @Test
    fun `a watchlist row whose lookup fails stays as the server sent it`() {
        answerWatchlist(lookups = false)
        val titles = withRepository { it.load(TitleSource.Watchlist, page = 1) }.results

        assertEquals(listOf(438631, 95396), titles.map { it.tmdbId })
        assertTrue(titles.all { it.artworkPath == null })
    }

    @Test
    fun `a list whose rows carry their own poster is not looked up again`() {
        answer(MIXED_PAGE)
        withRepository { it.load(TitleSource.Trending, page = 1) }

        assertEquals(1, seerr.requestCount)
    }

    @Test
    fun `a search with no query is answered without asking the server`() {
        val page = withRepository { it.load(TitleSource.Search("  "), page = 1) }

        assertTrue(page.results.isEmpty())
        assertFalse(page.hasMore)
        assertEquals(0, seerr.requestCount)
    }

    @Test
    fun `genre tiles take the first backdrop`() {
        answer(GENRE_SLIDER)
        val tiles = withRepository { it.genres(TitleKind.Movie) }

        assertEquals("/api/v1/discover/genreslider/movie", lastCall().encodedPath)
        assertEquals(listOf(GenreTile(28, "Action", "/a.jpg"), GenreTile(35, "Comedy", null)), tiles)
        withRepository { it.genres(TitleKind.Series) }
        assertEquals("/api/v1/discover/genreslider/tv", lastCall().encodedPath)
    }

    @Test
    fun `a server with no genre row gives no tiles and is not asked`() {
        profile = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = ServerVersion.of(1, 21, 0))
        val before = seerr.requestCount
        assertTrue(withRepository { it.genres(TitleKind.Movie) }.isEmpty())
        assertEquals(before, seerr.requestCount)
    }

    @Test
    fun `a source the server lacks is unsupported and refused`() {
        profile = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = ServerVersion.of(1, 8, 0))
        answer(MOVIES_PAGE)
        withRepository { repository ->
            assertFalse(repository.supports(TitleSource.ByKeyword(TitleKind.Movie, 1)))
            assertFalse(repository.supports(TitleSource.ByGenre(TitleKind.Movie, 1)))
            assertFalse(repository.supports(TitleSource.Upcoming(TitleKind.Series)))
            assertFalse(repository.supports(TitleSource.Watchlist))
            assertTrue(repository.supports(TitleSource.Upcoming(TitleKind.Movie)))
            assertTrue(repository.supports(TitleSource.Search("a")))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.load(TitleSource.Watchlist, 1) } }
        }
        assertEquals(0, seerr.requestCount)
    }

    @Test
    fun `each capability turns on at the version the endpoint table gives`() {
        fun overseerr(minor: Int) = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = ServerVersion.of(1, minor))
        assertTrue(overseerr(9).hasKeywordDiscover)
        assertFalse(overseerr(8).hasKeywordDiscover)
        assertTrue(overseerr(20).hasUpcomingTv)
        assertFalse(overseerr(19).hasUpcomingTv)
        assertTrue(overseerr(21).hasDiscoverByCategory)
        assertFalse(overseerr(20).hasDiscoverByCategory)
        assertTrue(overseerr(22).hasGenreSliders)
        assertFalse(overseerr(21).hasGenreSliders)
        assertTrue(overseerr(30).hasDiscoverWatchlist)
        assertFalse(overseerr(29).hasDiscoverWatchlist)
        val jellyseerr = SeerrServerProfile(variant = SeerrVariant.Jellyseerr, version = ServerVersion.of(1, 2, 0))
        assertTrue(jellyseerr.hasDiscoverWatchlist)
        assertFalse(SeerrServerProfile(variant = SeerrVariant.Jellyseerr, version = ServerVersion.of(1, 1, 0)).hasDiscoverWatchlist)
        assertTrue(jellyseerr.hasGenreSliders && jellyseerr.hasDiscoverByCategory && jellyseerr.hasKeywordDiscover)
    }
}
