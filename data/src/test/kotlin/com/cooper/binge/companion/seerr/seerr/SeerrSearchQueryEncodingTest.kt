package com.cooper.binge.companion.seerr.seerr

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The server refuses a raw `*`, `'`, `+` and the rest of the reserved set in a search value, so each search is sent encoded. */
class SeerrSearchQueryEncodingTest {
    private val server = MockWebServer().apply { start() }
    private val api =
        SeerrApiFactory(logRequests = false)
            .cached(server.url("/").toString(), SeerrAuth.ApiKey("key"))

    @After
    fun tearDown() = server.close()

    private fun target(call: suspend () -> Unit): String {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body("{}")
                .build(),
        )
        runBlocking { call() }
        return server.takeRequest().target
    }

    @Test
    fun `the title search encodes the reserved characters and keeps a space as %20`() {
        assertEquals("/api/v1/search?query=M%2AA%2AS%2AH&page=1", target { api.searchTitles("M*A*S*H") })
        assertEquals("/api/v1/search?query=what%27s%20up&page=1", target { api.searchTitles("what's up") })
        assertEquals("/api/v1/search?query=a%2Bb&page=1", target { api.searchTitles("a+b") })
        assertEquals("/api/v1/search?query=Am%C3%A9lie%20%26%20Co&page=1", target { api.searchTitles("Amélie & Co") })
    }

    @Test
    fun `the keyword and company searches encode the query`() {
        assertEquals("/api/v1/search/keyword?query=Q%2Abert&page=1", target { api.searchKeywords("Q*bert") })
        assertEquals("/api/v1/search/company?query=A%2AB&page=1", target { api.searchCompanies("A*B") })
    }

    @Test
    fun `the logs and the blocklist encode their search`() {
        assertEquals(
            "/api/v1/settings/logs?take=1&skip=0&filter=debug&search=a%2Ab",
            target { api.logs(take = 1, skip = 0, filter = "debug", search = "a*b") },
        )
        assertEquals(
            "/api/v1/blocklist?take=1&skip=0&search=a%2Ab",
            target { api.blocklist(path = "blocklist", take = 1, search = "a*b") },
        )
    }

    @Test
    fun `a request with no search is left as it was`() {
        assertEquals("/api/v1/discover/watchlist?page=2", target { api.discoverWatchlist(page = 2) })
    }
}
