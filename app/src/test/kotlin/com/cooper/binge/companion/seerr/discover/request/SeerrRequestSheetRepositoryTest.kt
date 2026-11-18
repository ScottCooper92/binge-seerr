package com.cooper.binge.companion.seerr.discover.request

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.PERMISSION_ADMIN
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST_ADVANCED
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
import retrofit2.HttpException

private val JSON = headersOf("Content-Type", "application/json")

private const val RADARR =
    """[{"id":1,"name":"Radarr","is4k":false,"isDefault":true,"activeProfileId":4,"activeDirectory":"/movies"},
       {"id":2,"name":"Radarr 4K","is4k":true,"isDefault":true,"activeProfileId":7,"activeDirectory":"/movies4k"}]"""
private const val RADARR_ONE =
    """{"server":{"id":1,"name":"Radarr"},"profiles":[{"id":4,"name":"HD"},{"id":5,"name":"Any"}],
       "rootFolders":[{"id":1,"path":"/movies"},{"id":2,"path":"/more"}],"tags":[]}"""
private const val QUOTA = """{"movie":{"days":7,"limit":10,"used":8,"remaining":2},"tv":{"limit":0}}"""

class SeerrRequestSheetRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var permissions = PERMISSION_ADMIN
    private var settings = SeerrPublicSettings(movie4kEnabled = true)
    private var postAnswer = FakeResponse(headers = JSON, body = """{"id":31}""")

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun route(request: FakeRequest): FakeResponse =
        when (request.url.encodedPath) {
            "/api/v1/service/radarr" -> FakeResponse(headers = JSON, body = RADARR)
            "/api/v1/service/sonarr" -> FakeResponse(headers = JSON, body = "[]")
            "/api/v1/service/radarr/1" -> FakeResponse(headers = JSON, body = RADARR_ONE)
            "/api/v1/user/9/quota" -> FakeResponse(headers = JSON, body = QUOTA)
            "/api/v1/request" -> postAnswer
            else -> FakeResponse(code = 404)
        }

    private fun <T> withRepository(block: suspend (RequestSheetRepository) -> T): T {
        seerr.dispatcher = ::route
        return runBlocking {
            factory.anonymous(seerr.url()) { api ->
                val profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = SeerrVersion(3, 2, 0), settings = settings)
                block(
                    SeerrRequestSheetRepository(
                        api = { api },
                        viewer = { SeerrUserDto(id = 9, permissions = permissions) },
                        profile = { profile },
                        io = Dispatchers.Unconfined,
                    ),
                )
            }
        }
    }

    @Test
    fun `an admin is offered 4K, both servers and the quota left`() {
        val options = withRepository { it.options(TitleKind.Movie) }

        assertTrue(options.may4k)
        assertEquals(listOf("Radarr", "Radarr 4K"), options.servers.map { it.name })
        assertEquals(listOf(false, true), options.servers.map { it.is4k })
        assertEquals(RequestQuota(limit = 10, remaining = 2, days = 7), options.quota)
        assertTrue(options.mayChooseDestination)
    }

    @Test
    fun `a request-only user has no destination to choose and no 4K`() {
        permissions = PERMISSION_REQUEST

        val options = withRepository { it.options(TitleKind.Movie) }

        assertFalse(options.may4k)
        assertTrue(options.servers.isEmpty())
        assertFalse(options.mayChooseDestination)
    }

    @Test
    fun `an advanced user without 4K is never offered a 4K server`() {
        permissions = PERMISSION_REQUEST or PERMISSION_REQUEST_ADVANCED

        val options = withRepository { it.options(TitleKind.Movie) }

        assertEquals(listOf("Radarr"), options.servers.map { it.name })
    }

    @Test
    fun `a type with no limit has no quota`() {
        val options = withRepository { it.options(TitleKind.Series) }

        assertNull(options.quota)
    }

    @Test
    fun `a server's choices are its profiles and root folders`() {
        val choices = withRepository { it.choices(TitleKind.Movie, serverId = 1) }

        assertEquals(listOf(RequestChoice(4, "HD"), RequestChoice(5, "Any")), choices.profiles)
        assertEquals(listOf("/movies", "/more"), choices.rootFolders)
    }

    @Test
    fun `a submit with a destination sends it and a created request is Created`() {
        val result =
            withRepository {
                it.submit(RequestSubmission(TitleKind.Movie, 603, emptyList(), is4k = true, RequestDestination(2, 7, "/movies4k")))
            }

        assertEquals(RequestResult.Created, result)
        val sent = seerr.requests.last { it.method == "POST" }.body
        assertTrue(sent, sent.contains("\"mediaType\":\"movie\"") && sent.contains("\"mediaId\":603") && sent.contains("\"is4k\":true"))
        assertTrue(
            sent,
            sent.contains("\"serverId\":2") && sent.contains("\"profileId\":7") && sent.contains("\"rootFolder\":\"/movies4k\""),
        )
    }

    @Test
    fun `a plain submit names no destination`() {
        withRepository { it.submit(RequestSubmission(TitleKind.Series, 1399, emptyList(), is4k = false, destination = null)) }

        val sent = seerr.requests.last { it.method == "POST" }.body
        assertTrue(sent, sent.contains("\"mediaType\":\"tv\"") && !sent.contains("serverId"))
    }

    @Test
    fun `the status code says nothing was left to request or that it was already requested`() {
        val submission = RequestSubmission(TitleKind.Movie, 603, emptyList(), is4k = false, destination = null)

        postAnswer = FakeResponse(code = 202, headers = JSON, body = """{"message":"nothing"}""")
        assertEquals(RequestResult.NothingToRequest, withRepository { it.submit(submission) })

        postAnswer = FakeResponse(code = 409, headers = JSON, body = """{"message":"exists"}""")
        assertEquals(RequestResult.AlreadyRequested, withRepository { it.submit(submission) })
    }

    @Test
    fun `a refusal is thrown as the call throws it`() {
        postAnswer = FakeResponse(code = 403, headers = JSON, body = """{"message":"Request quota exceeded"}""")

        val failure =
            assertThrows(HttpException::class.java) {
                withRepository { it.submit(RequestSubmission(TitleKind.Movie, 603, emptyList(), is4k = false, destination = null)) }
            }

        assertEquals(403, failure.code())
    }
}
