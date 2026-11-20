package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.discover.ExternalLink
import com.cooper.binge.companion.seerr.discover.ExternalLinkKind
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.collection.SeerrCollectionRepository
import com.cooper.binge.companion.seerr.seerr.PERMISSION_ADMIN
import com.cooper.binge.companion.seerr.seerr.PERMISSION_CREATE_ISSUES
import com.cooper.binge.companion.seerr.seerr.PERMISSION_MANAGE_BLOCKLIST
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST_MOVIE
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST_VIEW
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
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

private const val MOVIE =
    """{"id":603,"title":"The Matrix","tagline":"Free your mind","overview":"A hacker.","posterPath":"/p.jpg","backdropPath":"/b.jpg",
"releaseDate":"1999-03-30","runtime":136,"status":"Released","voteAverage":8.2,"voteCount":24000,"budget":63000000,"revenue":0,
"originalLanguage":"en","genres":[{"id":28,"name":"Action"}],"keywords":[{"id":1,"name":"simulation"}],
"releases":{"results":[{"iso_3166_1":"GB","release_dates":[{"certification":"15"}]},{"iso_3166_1":"US","release_dates":[{"certification":""},{"certification":"R"}]}]},
"credits":{"cast":[{"id":2,"name":"B","character":"Trinity","order":1},{"id":1,"name":"A","character":"Neo","order":0}],
"crew":[{"id":10,"name":"Dir","job":"Director"},{"id":11,"name":"Wri","job":"Writer"},{"id":11,"name":"Wri","job":"Screenplay"},{"id":12,"name":"Cam","job":"Cinematography"}]},
"collection":{"id":2344,"name":"The Matrix Collection"},
"relatedVideos":[{"key":"k","name":"Trailer","site":"YouTube","url":"https://www.youtube.com/watch?v=k"}],
"watchProviders":[{"iso_3166_1":"US","flatrate":[{"id":8,"name":"Stream US","displayPriority":2},{"id":9,"name":"First US","displayPriority":1}]},
{"iso_3166_1":"GB","flatrate":[{"id":8,"name":"Stream GB"}]}],
"mediaInfo":{"id":11,"status":5,"status4k":1}}"""
private const val RATINGS = """{"rt":{"criticsScore":83,"audienceScore":85},"imdb":{"criticsScore":8.7}}"""
private const val PAGE =
    """{"page":1,"totalPages":1,"results":[{"id":604,"mediaType":"movie","title":"Reloaded","releaseDate":"2003-05-15"}]}"""

private const val COLLECTION =
    """{"id":2344,"name":"The Matrix Collection","parts":[
        {"id":604,"mediaType":"movie","title":"Reloaded","releaseDate":"2003-05-15"},
        {"id":603,"mediaType":"movie","title":"The Matrix","releaseDate":"1999-03-30","posterPath":"/m.jpg","voteAverage":8.1,"mediaInfo":{"id":11,"status":5}}]}"""

class SeerrTitleDetailsRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var permissions = PERMISSION_ADMIN
    private var variant = SeerrVariant.Seerr
    private var version = SeerrVersion(3, 2, 0)
    private var settings = SeerrPublicSettings(movie4kEnabled = true, streamingRegion = "GB", discoverRegion = "US")
    private var deviceRegion: String? = null
    private var userRegion = """{"streamingRegion":"US"}"""
    private var movieBody = MOVIE
    private val unblocks = mutableListOf<String>()
    private val blocks = mutableListOf<String>()
    private var blockCode = 201
    private val watchlistCalls = mutableListOf<String>()
    private var watchlistFails = false
    private val statuses = TitleStatusStore()
    private var ratingsAnswer = FakeResponse(headers = JSON, body = RATINGS)

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun recordUnblock(request: FakeRequest): FakeResponse {
        unblocks += "${request.method} ${request.url.encodedPath}?${request.url.encodedQuery}"
        return FakeResponse()
    }

    private fun recordBlock(request: FakeRequest): FakeResponse {
        blocks += "${request.method} ${request.url.encodedPath} ${request.body}"
        return FakeResponse(code = blockCode)
    }

    private fun recordWatchlist(request: FakeRequest): FakeResponse {
        watchlistCalls += "${request.method} ${request.url.encodedPath}?${request.url.encodedQuery} ${request.body}"
        val code =
            when {
                watchlistFails -> 500
                request.method == "POST" -> 201
                else -> 204
            }
        return FakeResponse(code = code)
    }

    private fun route(request: FakeRequest): FakeResponse =
        when (request.url.encodedPath) {
            "/api/v1/movie/603" -> FakeResponse(headers = JSON, body = movieBody)
            "/api/v1/movie/603/ratingscombined" -> ratingsAnswer
            "/api/v1/movie/603/recommendations" -> FakeResponse(headers = JSON, body = PAGE)
            "/api/v1/movie/603/similar" -> FakeResponse(code = 500)
            "/api/v1/collection/2344" -> FakeResponse(headers = JSON, body = COLLECTION)
            "/api/v1/user/9/settings/main" -> FakeResponse(headers = JSON, body = userRegion)
            "/api/v1/blocklist/603", "/api/v1/blacklist/603" -> recordUnblock(request)
            "/api/v1/blocklist", "/api/v1/blacklist" -> recordBlock(request)
            "/api/v1/watchlist", "/api/v1/watchlist/603" -> recordWatchlist(request)
            else -> FakeResponse(code = 404)
        }

    private fun <T> withRepository(block: suspend (TitleDetailsRepository) -> T): T {
        seerr.dispatcher = ::route
        return runBlocking {
            factory.anonymous(seerr.url()) { api ->
                val profile = SeerrServerProfile(variant = variant, version = version, settings = settings)
                block(
                    SeerrTitleDetailsRepository(
                        api = { api },
                        viewer = { SeerrUserDto(id = 9, permissions = permissions) },
                        profile = { profile },
                        serverUrl = { "https://seerr.example/" },
                        deviceRegion = { deviceRegion },
                        collections =
                            SeerrCollectionRepository(
                                api = { api },
                                viewer = { SeerrUserDto(id = 9, permissions = permissions) },
                                profile = { profile },
                                io = Dispatchers.Unconfined,
                            ),
                        io = Dispatchers.Unconfined,
                        statuses = statuses,
                    ),
                )
            }
        }
    }

    @Test
    fun `a collection's films map as movies, in release order, with the status it holds`() {
        val parts = withRepository { it.collectionParts(2344) }

        assertEquals(listOf(603, 604), parts.map { it.tmdbId })
        assertTrue(parts.all { it.kind == TitleKind.Movie })
        assertEquals(TitleAvailability.Available, parts[0].availability)
        assertEquals(TitleAvailability.None, parts[1].availability)
        assertEquals("2003", parts[1].year)
        assertEquals(8.1, parts[0].rating)
        assertNull(parts[1].rating)
    }

    @Test
    fun `the page maps the facts the server gave and leaves out the empty ones`() {
        val movie = withRepository { it.movie(603) }

        assertEquals("The Matrix", movie.title)
        assertEquals("1999", movie.year)
        assertEquals(136, movie.runtimeMinutes)
        assertEquals("Free your mind", movie.tagline)
        assertEquals("https://seerr.example/movie/603", movie.webUrl)
        assertNull("a zero revenue is not shown", movie.revenue)
        assertEquals(63_000_000L, movie.budget)
        assertEquals(listOf("Neo", "Trinity"), movie.cast.map { it.role })
        assertEquals(11, movie.mediaId)
    }

    @Test
    fun `key crew is each person once under all their key jobs and the rest is other crew`() {
        val movie = withRepository { it.movie(603) }

        assertEquals(listOf("Dir" to "Director", "Wri" to "Writer, Screenplay"), movie.keyCrew.map { it.name to it.role })
        assertEquals(listOf("Cam"), movie.otherCrew.map { it.name })
    }

    @Test
    fun `a person listed twice in the cast or the other crew appears once`() {
        movieBody =
            MOVIE
                .replace(
                    """{"id":1,"name":"A","character":"Neo","order":0}]""",
                    """{"id":1,"name":"A","character":"Neo","order":0},{"id":1,"name":"A","character":"The One","order":2}]""",
                ).replace(
                    """{"id":12,"name":"Cam","job":"Cinematography"}""",
                    """{"id":12,"name":"Cam","job":"Cinematography"},{"id":12,"name":"Cam","job":"Camera Operator"}""",
                )

        val movie = withRepository { it.movie(603) }

        assertEquals(listOf(1, 2), movie.cast.map { it.personId })
        assertEquals("Neo, The One", movie.cast.first().role)
        assertEquals(listOf(12), movie.otherCrew.map { it.personId })
        assertEquals("Cinematography, Camera Operator", movie.otherCrew.single().role)
    }

    @Test
    fun `providers and certification follow the user's streaming region then the server's`() {
        val own = withRepository { it.movie(603) }
        assertEquals("US", own.providers?.region)
        assertEquals(listOf("First US", "Stream US"), own.providers?.streaming?.map { it.name })
        assertEquals("R", own.certification)

        userRegion = "{}"
        val server = withRepository { it.movie(603) }
        assertEquals("GB", server.providers?.region)
        assertEquals("15", server.certification)
    }

    @Test
    fun `studios may be links only where the server can list what a studio made`() {
        assertTrue(withRepository { it.movie(603) }.companiesOpen)
        variant = SeerrVariant.Overseerr
        version = SeerrVersion(1, 20, 0)
        assertFalse(withRepository { it.movie(603) }.companiesOpen)
        version = SeerrVersion(1, 21, 0)
        assertTrue(withRepository { it.movie(603) }.companiesOpen)
    }

    @Test
    fun `keywords keep their ids and may be links only where the server can filter by keyword`() {
        val movie = withRepository { it.movie(603) }
        assertEquals(listOf(TitleKeyword(1, "simulation")), movie.keywords)
        assertTrue(movie.keywordsOpen)
        variant = SeerrVariant.Overseerr
        version = SeerrVersion(1, 8, 0)
        assertFalse(withRepository { it.movie(603) }.keywordsOpen)
        version = SeerrVersion(1, 9, 0)
        assertTrue(withRepository { it.movie(603) }.keywordsOpen)
    }

    @Test
    fun `with no region set anywhere the phone's country is the region`() {
        settings = SeerrPublicSettings(movie4kEnabled = true)
        userRegion = "{}"
        deviceRegion = "GB"

        val movie = withRepository { it.movie(603) }

        assertEquals("GB", movie.providers?.region)
        assertEquals(listOf("Stream GB"), movie.providers?.streaming?.map { it.name })
        assertEquals("15", movie.certification)
    }

    @Test
    fun `a region someone chose wins over the phone's country`() {
        deviceRegion = "GB"

        assertEquals("US", withRepository { it.movie(603) }.providers?.region)

        userRegion = "{}"
        assertEquals("GB", withRepository { it.movie(603) }.providers?.region)
        settings = SeerrPublicSettings(movie4kEnabled = true, streamingRegion = "US")
        assertEquals("US", withRepository { it.movie(603) }.providers?.region)
    }

    @Test
    fun `with no region anywhere and none on the phone there are no providers`() {
        settings = SeerrPublicSettings(movie4kEnabled = true)
        userRegion = "{}"
        deviceRegion = null

        assertNull(withRepository { it.movie(603) }.providers)
    }

    @Test
    fun `a title the server holds nothing of in a version can be requested in it`() {
        val admin = withRepository { it.movie(603) }
        assertEquals(TitleAvailability.Available, admin.standard.availability)
        assertFalse("an available title is not requested again", admin.standard.mayRequest)
        assertEquals(TitleAvailability.None, admin.fourK?.availability)
        assertTrue(admin.fourK?.mayRequest == true)
    }

    @Test
    fun `a user who may not request 4K is not offered it and one who may not request a film has no request`() {
        permissions = PERMISSION_REQUEST or PERMISSION_REQUEST_MOVIE
        val plain = withRepository { it.movie(603) }
        assertNull(plain.fourK)

        permissions = 0
        val none = withRepository { it.movie(603) }
        assertFalse(none.standard.mayRequest)
    }

    @Test
    fun `a title can be reported on only where the server takes issues and the viewer may make one`() {
        assertTrue(withRepository { it.movie(603) }.mayReport)

        permissions = PERMISSION_REQUEST or PERMISSION_REQUEST_MOVIE
        assertFalse("no issue permission", withRepository { it.movie(603) }.mayReport)

        permissions = PERMISSION_CREATE_ISSUES
        assertTrue(withRepository { it.movie(603) }.mayReport)

        variant = SeerrVariant.Overseerr
        version = SeerrVersion(1, 27, 0)
        assertFalse("an Overseerr before issues", withRepository { it.movie(603) }.mayReport)
    }

    @Test
    fun `a title can be unblocked only where the server has a blocklist and the viewer may manage it`() {
        assertTrue(withRepository { it.movie(603) }.mayUnblock)

        permissions = PERMISSION_REQUEST
        assertFalse("no blocklist permission", withRepository { it.movie(603) }.mayUnblock)

        permissions = PERMISSION_MANAGE_BLOCKLIST
        assertTrue(withRepository { it.movie(603) }.mayUnblock)

        variant = SeerrVariant.Overseerr
        version = SeerrVersion(1, 27, 0)
        assertFalse("an Overseerr has no blocklist", withRepository { it.movie(603) }.mayUnblock)
    }

    @Test
    fun `the links are the ones the server named and TMDB's own page`() {
        assertEquals(
            listOf(ExternalLink(ExternalLinkKind.Tmdb, "https://www.themoviedb.org/movie/603")),
            withRepository { it.movie(603) }.links,
        )

        movieBody = MOVIE.replaceFirst("{", """{"homepage":"https://matrix.example.org/","externalIds":{"imdbId":"tt0133093"},""")
        assertEquals(
            listOf(
                ExternalLink(ExternalLinkKind.Imdb, "https://www.imdb.com/title/tt0133093"),
                ExternalLink(ExternalLinkKind.Tmdb, "https://www.themoviedb.org/movie/603"),
                ExternalLink(ExternalLinkKind.Website, "https://matrix.example.org/"),
            ),
            withRepository { it.movie(603) }.links,
        )
    }

    @Test
    fun `the right to block is the right to unblock`() {
        assertTrue(withRepository { it.movie(603) }.mayBlock)

        permissions = PERMISSION_REQUEST
        assertFalse("no blocklist permission", withRepository { it.movie(603) }.mayBlock)

        variant = SeerrVariant.Overseerr
        version = SeerrVersion(1, 27, 0)
        permissions = PERMISSION_MANAGE_BLOCKLIST
        assertFalse("an Overseerr has no blocklist", withRepository { it.movie(603) }.mayBlock)
    }

    @Test
    fun `blocking sends the film with the signed in user and reads an already blocked title as done`() {
        withRepository { it.block(603, "The Matrix") }
        assertEquals(1, blocks.size)
        assertTrue(blocks.single().startsWith("POST /api/v1/blocklist "))
        assertTrue(blocks.single().contains(""""tmdbId":603"""))
        assertTrue(blocks.single().contains(""""mediaType":"movie""""))
        assertTrue(blocks.single().contains(""""title":"The Matrix""""))

        blockCode = 412
        withRepository { it.block(603, "The Matrix") }
        assertEquals(2, blocks.size)
    }

    @Test
    fun `blocking a title the server refuses throws`() {
        blockCode = 403
        val failure = runCatching { withRepository { it.block(603, "The Matrix") } }.exceptionOrNull()
        assertTrue(failure is retrofit2.HttpException)
    }

    @Test
    fun `unblocking names the movie on a server that needs the media type and omits it on one that refuses it`() {
        withRepository { it.unblock(603) }
        assertEquals(listOf("DELETE /api/v1/blocklist/603?mediaType=movie"), unblocks)

        unblocks.clear()
        version = SeerrVersion(3, 0, 0)
        withRepository { it.unblock(603) }
        assertEquals(listOf("DELETE /api/v1/blocklist/603?null"), unblocks)
    }

    @Test
    fun `the details say whether the viewer has the title on their watchlist`() {
        assertFalse(withRepository { it.movie(603) }.onWatchlist)

        movieBody = MOVIE.replaceFirst("{", """{"onUserWatchlist":true,""")
        assertTrue(withRepository { it.movie(603) }.onWatchlist)
    }

    @Test
    fun `the watchlist button is offered only where the server can edit the list`() {
        assertTrue(withRepository { it.movie(603) }.mayWatchlist)

        variant = SeerrVariant.Overseerr
        version = SeerrVersion(1, 33, 0)
        assertFalse("an Overseerr can read the watchlist and never edit it", withRepository { it.movie(603) }.mayWatchlist)

        variant = SeerrVariant.Jellyseerr
        version = SeerrVersion(1, 5, 0)
        assertFalse("a Jellyseerr before the edit calls", withRepository { it.movie(603) }.mayWatchlist)

        version = SeerrVersion(1, 6, 0)
        assertTrue(withRepository { it.movie(603) }.mayWatchlist)
    }

    @Test
    fun `adding to the watchlist posts the title and removing it deletes by id with the media type`() {
        withRepository { it.setWatchlist(603, "The Matrix", onList = true) }
        withRepository { it.setWatchlist(603, "The Matrix", onList = false) }

        assertEquals(
            listOf(
                "POST /api/v1/watchlist?null {\"tmdbId\":603,\"mediaType\":\"movie\",\"title\":\"The Matrix\"}",
                "DELETE /api/v1/watchlist/603?mediaType=movie ",
            ),
            watchlistCalls,
        )
    }

    @Test
    fun `a title put on the watchlist shows on it in the store at once`() {
        withRepository { it.setWatchlist(603, "The Matrix", onList = true) }

        assertEquals(true, statuses.current(TitleKey(TitleType.Movie, 603))?.onWatchlist)

        withRepository { it.setWatchlist(603, "The Matrix", onList = false) }

        assertEquals(false, statuses.current(TitleKey(TitleType.Movie, 603))?.onWatchlist)
    }

    @Test
    fun `a watchlist call the server refuses leaves the store as it was`() {
        withRepository { it.setWatchlist(603, "The Matrix", onList = true) }
        watchlistFails = true

        assertThrows(Exception::class.java) { withRepository { it.setWatchlist(603, "The Matrix", onList = false) } }

        assertEquals(true, statuses.current(TitleKey(TitleType.Movie, 603))?.onWatchlist)
    }

    @Test
    fun `a viewer who may not view requests keeps only their own among the title's requests`() {
        movieBody =
            MOVIE.replace(
                """"mediaInfo":{"id":11,"status":5,"status4k":1}""",
                """"mediaInfo":{"id":11,"status":5,"status4k":1,"requests":[
                    {"id":1,"status":1,"is4k":false,"requestedBy":{"id":9}},
                    {"id":2,"status":2,"is4k":false,"requestedBy":{"id":4}},
                    {"id":3,"status":1,"is4k":false}]}""",
            )

        permissions = PERMISSION_REQUEST or PERMISSION_REQUEST_MOVIE
        assertEquals("their own only", listOf(1), withRepository { it.movie(603) }.requests.map { it.id })

        permissions = PERMISSION_REQUEST_VIEW
        assertEquals(
            "everyone's with permission to view requests",
            listOf(1, 2, 3),
            withRepository { it.movie(603) }.requests.map { it.id },
        )

        permissions = PERMISSION_ADMIN
        assertEquals(listOf(1, 2, 3), withRepository { it.movie(603) }.requests.map { it.id })
    }

    @Test
    fun `the watch link is the first the server filled in, standard before 4K, skipping a blank one`() {
        fun body(info: String) =
            MOVIE.replace(
                "\"mediaInfo\":{\"id\":11,\"status\":5,\"status4k\":1}",
                "\"mediaInfo\":{\"id\":11,\"status\":5,\"status4k\":1,$info}",
            )

        movieBody = body("\"mediaUrl\":\"\",\"jellyfinMediaUrl\":\"https://jf.example/item\",\"mediaUrl4k\":\"https://jf.example/4k\"")
        assertEquals("a blank link is skipped", "https://jf.example/item", withRepository { it.movie(603) }.watchUrl)

        movieBody = body("\"mediaUrl4k\":\"https://jf.example/4k\"")
        assertEquals("a title held only in 4K still opens", "https://jf.example/4k", withRepository { it.movie(603) }.watchUrl)

        movieBody = body("\"mediaUrl\":\"https://media.invalid/x\"")
        assertNull("not a real site", withRepository { it.movie(603) }.watchUrl)

        movieBody = body("\"mediaUrl\":\"https://media.invalid/x\",\"mediaUrl4k\":\"https://jf.example/4k\"")
        assertEquals("an unopenable standard link leaves the 4K one", "https://jf.example/4k", withRepository { it.movie(603) }.watchUrl)
    }

    @Test
    fun `the details say whether the viewer's account may request a film at all`() {
        assertTrue(withRepository { it.movie(603) }.canRequest)

        permissions = 0
        assertFalse(withRepository { it.movie(603) }.canRequest)
    }

    @Test
    fun `extras carry the combined ratings only where the server has them and a failed part is empty`() {
        val extras = withRepository { it.extras(TitleKind.Movie, 603) }

        assertEquals(TitleRatings(83, 85, 8.7), extras.ratings)
        assertEquals(listOf("Reloaded"), extras.recommendations.map { it.name })
        assertTrue("a part that failed leaves only itself out", extras.similar.isEmpty())

        val calls = { seerr.requests.count { it.url.encodedPath.endsWith("ratingscombined") } }
        val before = calls()
        variant = SeerrVariant.Overseerr
        version = SeerrVersion(1, 33, 2)
        assertNull(withRepository { it.extras(TitleKind.Movie, 603) }.ratings)
        assertEquals("Overseerr below 1.34 is not asked", before, calls())

        version = SeerrVersion(1, 34, 0)
        assertEquals(TitleRatings(83, 85, 8.7), withRepository { it.extras(TitleKind.Movie, 603) }.ratings)
    }

    @Test
    fun `a server with neither score has no ratings`() {
        ratingsAnswer = FakeResponse(code = 404)
        assertNull(withRepository { it.extras(TitleKind.Movie, 603) }.ratings)
    }

    @Test
    fun `videos are each once, trailers first, and only a few`() {
        val videos =
            listOf(
                """{"key":"a","name":"Clip A","site":"YouTube","type":"Clip","url":"https://y/a"}""",
                """{"key":"a","name":"Clip A again","site":"YouTube","type":"Clip","url":"https://y/a"}""",
                """{"key":"b","name":"In Cinemas Now","site":"YouTube","type":"Teaser","url":"https://y/b"}""",
                """{"key":"t","name":"Trailer","site":"YouTube","type":"Trailer","url":"https://y/t"}""",
                """{"key":"c","name":"Clip C","site":"YouTube","type":"Clip","url":"https://y/c"}""",
            )
        movieBody = MOVIE.replace(Regex(""""relatedVideos":\[.*?\]"""), "\"relatedVideos\":[${videos.joinToString(",")}]")

        val movie = withRepository { it.movie(603) }

        assertEquals(listOf("Trailer", "Clip A", "In Cinemas Now"), movie.videos.map { it.name })
    }

    @Test
    fun `spoken languages are codes, formatted by the screen`() {
        movieBody =
            MOVIE.replace(
                "\"genres\"",
                "\"spokenLanguages\":[{\"iso_639_1\":\"en\",\"name\":\"English\"},{\"iso_639_1\":\"fr\",\"name\":\"French\"}],\"genres\"",
            )

        assertEquals(listOf("en", "fr"), withRepository { it.movie(603) }.spokenLanguageCodes)
    }
}
