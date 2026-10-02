package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class SeerrApiFactoryTest {
    private val factory = SeerrApiFactory(logRequests = false)
    private val auth = SeerrAuth.ApiKey("key")

    @Test
    fun `the saved server is served from one cached client`() {
        val first = factory.cached(BASE_URL, auth)

        assertSame(first, factory.cached(BASE_URL, auth))
    }

    @Test
    fun `changed credentials rebuild the client`() {
        val first = factory.cached(BASE_URL, auth)

        assertNotSame(first, factory.cached(BASE_URL, SeerrAuth.ApiKey("other")))
    }

    @Test
    fun `evict releases the cached client so a reconnect starts fresh`() {
        val first = factory.cached(BASE_URL, auth)

        factory.evict()

        assertNotSame(first, factory.cached(BASE_URL, auth))
    }

    @Test
    fun `every client fails a dark host in five seconds but still waits fifteen for a slow read`() {
        val seen = mutableListOf<Pair<Int, Int>>()
        val recording =
            SeerrApiFactory(
                logRequests = false,
                testTransport = {
                    Interceptor { chain ->
                        seen += chain.connectTimeoutMillis() to chain.readTimeoutMillis()
                        throw IOException("no socket in this test")
                    }
                },
            )

        runBlocking {
            recording.probe(BASE_URL, auth) { runCatching { it.requestCount() } }
            recording.login(BASE_URL) { runCatching { it.requestCount() } }
            recording.anonymous(BASE_URL) { runCatching { it.requestCount() } }
            runCatching { recording.cached(BASE_URL, auth).requestCount() }
        }

        assertEquals(4, seen.size)
        seen.forEach { (connect, read) ->
            assertEquals(5_000, connect)
            assertEquals(15_000, read)
        }
        assertEquals(5L, CONNECT_TIMEOUT_SECONDS)
        assertEquals(15L, READ_TIMEOUT_SECONDS)
    }

    private companion object {
        const val BASE_URL = "https://seerr.example/"
    }
}
