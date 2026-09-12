package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

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

    private companion object {
        const val BASE_URL = "https://seerr.example/"
    }
}
