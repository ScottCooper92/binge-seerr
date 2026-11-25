package com.cooper.binge.companion.seerr.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.seerr.PERMISSION_ADMIN
import com.cooper.binge.companion.seerr.seerr.PERMISSION_REQUEST
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.homelab.core.foundation.ServerVersion
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

private const val COLLECTION =
    """{"id":2344,"name":"The Matrix Collection","overview":"  ","posterPath":"/poster.jpg","backdropPath":"/back.jpg","parts":[
        {"id":604,"mediaType":"movie","title":"Reloaded","releaseDate":"2003-05-15"},
        {"id":603,"mediaType":"movie","title":"The Matrix","releaseDate":"1999-03-30","mediaInfo":{"id":11,"status":5,"status4k":2}},
        {"id":900,"mediaType":"movie","title":"Undated"}]}"""

class SeerrCollectionRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var body = COLLECTION
    private var permissions = PERMISSION_ADMIN
    private var variant = SeerrVariant.Seerr
    private var version = ServerVersion.of(3, 2, 0)
    private val calls = mutableListOf<String>()

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun route(request: FakeRequest): FakeResponse =
        when (request.url.encodedPath) {
            "/api/v1/collection/2344" -> FakeResponse(headers = JSON, body = body)
            "/api/v1/blocklist/collection/2344" -> FakeResponse(code = 204).also { calls += request.method }
            else -> FakeResponse(code = 404)
        }

    private fun <T> withRepository(block: suspend (CollectionRepository) -> T): T {
        seerr.dispatcher = ::route
        return runBlocking {
            factory.anonymous(seerr.url()) { api ->
                val profile =
                    SeerrServerProfile(variant = variant, version = version, settings = SeerrPublicSettings(movie4kEnabled = false))
                block(
                    SeerrCollectionRepository(
                        { api },
                        { SeerrUserDto(id = 9, permissions = permissions) },
                        { profile },
                        Dispatchers.Unconfined,
                    ),
                )
            }
        }
    }

    private fun read(): CollectionDetails = withRepository { it.collection(2344) }

    @Test
    fun `the films come oldest first with the undated last and each one's status`() {
        val collection = read()

        assertEquals(listOf(603, 604, 900), collection.parts.map { it.tmdbId })
        assertEquals(TitleAvailability.Available, collection.parts[0].availability)
        assertEquals(2, collection.toRequest)
    }

    @Test
    fun `a blank overview is no overview and the backdrop is the collection's own`() {
        val collection = read()

        assertNull(collection.overview)
        assertEquals(true, collection.backdropUrl?.endsWith("/back.jpg"))
    }

    @Test
    fun `each film's 4K status is read apart from its standard one`() {
        val collection = read()

        assertEquals(TitleAvailability.Pending, collection.parts4k[603])
        assertEquals(TitleAvailability.None, collection.parts4k[604])
    }

    @Test
    fun `a viewer who manages the blocklist on a server with the endpoint may block`() {
        assertTrue(withRepository { it.mayBlock() })
    }

    @Test
    fun `a viewer without the blocklist right may not`() {
        permissions = PERMISSION_REQUEST

        assertFalse(withRepository { it.mayBlock() })
    }

    @Test
    fun `a server older than the endpoint may not`() {
        version = ServerVersion.of(3, 1, 0)

        assertFalse(withRepository { it.mayBlock() })
    }

    @Test
    fun `blocking posts and unblocking deletes`() {
        withRepository {
            it.setBlocked(2344, true)
            it.setBlocked(2344, false)
        }

        assertEquals(listOf("POST", "DELETE"), calls)
    }

    @Test
    fun `a collection counts as blocked only when every film is`() {
        body = """{"id":2344,"name":"C","parts":[{"id":1,"mediaType":"movie","title":"A","mediaInfo":{"id":1,"status":6}},
            {"id":2,"mediaType":"movie","title":"B","mediaInfo":{"id":2,"status":6}}]}"""
        assertTrue(read().blocked)

        body = """{"id":2344,"name":"C","parts":[{"id":1,"mediaType":"movie","title":"A","mediaInfo":{"id":1,"status":6}},
            {"id":2,"mediaType":"movie","title":"B"}]}"""
        assertFalse(read().blocked)
    }
}
