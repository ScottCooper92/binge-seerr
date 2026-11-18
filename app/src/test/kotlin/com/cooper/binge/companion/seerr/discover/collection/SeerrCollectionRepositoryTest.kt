package com.cooper.binge.companion.seerr.discover.collection

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val JSON = headersOf("Content-Type", "application/json")

private const val COLLECTION =
    """{"id":2344,"name":"The Matrix Collection","overview":"  ","posterPath":"/poster.jpg","backdropPath":"/back.jpg","parts":[
        {"id":604,"mediaType":"movie","title":"Reloaded","releaseDate":"2003-05-15"},
        {"id":603,"mediaType":"movie","title":"The Matrix","releaseDate":"1999-03-30","mediaInfo":{"id":11,"status":5}},
        {"id":900,"mediaType":"movie","title":"Undated"}]}"""

class SeerrCollectionRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var body = COLLECTION

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun route(request: FakeRequest): FakeResponse =
        if (request.url.encodedPath == "/api/v1/collection/2344") FakeResponse(headers = JSON, body = body) else FakeResponse(code = 404)

    private fun read(): CollectionDetails {
        seerr.dispatcher = ::route
        return runBlocking {
            factory.anonymous(seerr.url()) { api -> SeerrCollectionRepository({ api }, Dispatchers.Unconfined).collection(2344) }
        }
    }

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
}
