package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleStatuses
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
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
import org.junit.Test

private val JSON = headersOf("Content-Type", "application/json")

/** A media row as `GET /media` sends it: the standard and the 4K status are the server's own and are kept apart. */
private const val MEDIA_PAGE = """{"results":[{"id":5,"tmdbId":1,"mediaType":"movie","status":5,"status4k":3}]}"""

/** A request row carries the same media object under `media`. */
private const val REQUEST_PAGE = """{"results":[
{"id":9,"status":2,"media":{"id":6,"tmdbId":2,"mediaType":"tv","status":2,"status4k":5}}]}"""

/** The hub's two non-title rows write what they read into the status store, so every card follows it (#2590). */
class SeerrHubMediaSourceTest {
    private val seerr = FakeSeerrServer()
    private val factory =
        SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private val statuses = TitleStatusStore()

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun <T> withSource(
        body: String,
        block: suspend (SeerrHubMediaSource) -> T,
    ): T {
        seerr.dispatcher = { FakeResponse(headers = JSON, body = body) }
        return runBlocking {
            factory.anonymous(seerr.url()) { api ->
                val profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = ServerVersion.of(3, 2, 0))
                block(
                    SeerrHubMediaSource(
                        api = { api },
                        profile = { profile },
                        titles = TitleCache(FakeTitleDao()),
                        io = Dispatchers.Unconfined,
                        serverUrl = { "https://s/" },
                        statuses = statuses,
                    ),
                )
            }
        }
    }

    @Test
    fun `recently added writes both versions of each title into the store`() {
        withSource(MEDIA_PAGE) { it.recentlyAdded() }

        val expected = TitleStatuses(SeerrMediaStatusCode.Available, SeerrMediaStatusCode.Processing, 5)
        assertEquals(expected, statuses.current(TitleKey(TitleType.Movie, 1)))
    }

    @Test
    fun `recent requests write both versions of each title into the store`() {
        withSource(REQUEST_PAGE) { it.recentRequests() }

        val expected = TitleStatuses(SeerrMediaStatusCode.Pending, SeerrMediaStatusCode.Available, 6)
        assertEquals(expected, statuses.current(TitleKey(TitleType.Tv, 2)))
    }
}
