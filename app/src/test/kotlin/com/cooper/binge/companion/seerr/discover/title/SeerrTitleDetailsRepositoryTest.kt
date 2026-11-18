package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.PERMISSION_ADMIN
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST_MOVIE
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
        {"id":603,"mediaType":"movie","title":"The Matrix","releaseDate":"1999-03-30","posterPath":"/m.jpg","mediaInfo":{"id":11,"status":5}},
        {"id":604,"mediaType":"movie","title":"Reloaded","releaseDate":"2003-05-15"}]}"""

class SeerrTitleDetailsRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var permissions = PERMISSION_ADMIN
    private var variant = SeerrVariant.Seerr
    private var version = SeerrVersion(3, 2, 0)
    private var settings = SeerrPublicSettings(movie4kEnabled = true, streamingRegion = "GB", discoverRegion = "US")
    private var userRegion = """{"streamingRegion":"US"}"""
    private var movieBody = MOVIE
    private var ratingsAnswer = FakeResponse(headers = JSON, body = RATINGS)

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun route(request: FakeRequest): FakeResponse =
        when (request.url.encodedPath) {
            "/api/v1/movie/603" -> FakeResponse(headers = JSON, body = movieBody)
            "/api/v1/movie/603/ratingscombined" -> ratingsAnswer
            "/api/v1/movie/603/recommendations" -> FakeResponse(headers = JSON, body = PAGE)
            "/api/v1/movie/603/similar" -> FakeResponse(code = 500)
            "/api/v1/collection/2344" -> FakeResponse(headers = JSON, body = COLLECTION)
            "/api/v1/user/9/settings/main" -> FakeResponse(headers = JSON, body = userRegion)
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
                        io = Dispatchers.Unconfined,
                    ),
                )
            }
        }
    }

    @Test
    fun `a collection's films map as movies, in the order the server gave, with the status it holds`() {
        val parts = withRepository { it.collectionParts(2344) }

        assertEquals(listOf(603, 604), parts.map { it.tmdbId })
        assertTrue(parts.all { it.kind == TitleKind.Movie })
        assertEquals(TitleAvailability.Available, parts[0].availability)
        assertEquals(TitleAvailability.None, parts[1].availability)
        assertEquals("2003", parts[1].year)
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
