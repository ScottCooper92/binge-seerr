package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The carried form. A carrier holds what an older build wrote and hands it to a newer one, so what
 * matters is that a round trip is lossless and that anything else is refused rather than guessed at.
 */
class ConnectionCarrierTest {
    private fun roundTrip(credentials: SeerrCredentials): SeerrCredentials? = decodeCarriedConnection(encodeCarriedConnection(credentials))

    @Test
    fun `a key connection survives the round trip whole`() {
        val credentials = SeerrCredentials("https://seerr.example/", SeerrAuth.ApiKey("k3y"), SeerrVariant.Jellyseerr)

        assertEquals(credentials, roundTrip(credentials))
    }

    @Test
    fun `a session connection keeps its cookie and the user it belongs to`() {
        val credentials = SeerrCredentials("https://seerr.example/", SeerrAuth.Session("connect.sid=abc", 7), SeerrVariant.Seerr)

        assertEquals(credentials, roundTrip(credentials))
    }

    @Test
    fun `the payload is small enough for the carrier to take`() {
        val credentials = SeerrCredentials("https://seerr.example/", SeerrAuth.Session("connect.sid=${"a".repeat(512)}", 7))

        // Block Store's ceiling is 4096 bytes, and a session cookie is the largest thing carried.
        assertTrue(encodeCarriedConnection(credentials).size < 4096)
    }

    @Test
    fun `a payload this build cannot use is refused rather than half-read`() {
        assertNull(decodeCarriedConnection("not json".toByteArray()))
        assertNull(decodeCarriedConnection("""{"url":"https://a/","kind":"webauthn","secret":"s"}""".toByteArray()))
        assertNull(decodeCarriedConnection("""{"url":"","kind":"api_key","secret":"s"}""".toByteArray()))
        assertNull(decodeCarriedConnection("""{"url":"https://a/","kind":"api_key","secret":""}""".toByteArray()))
    }

    @Test
    fun `a session with no user on it is refused, because the id cannot be guessed`() {
        assertNull(decodeCarriedConnection("""{"url":"https://a/","kind":"session","secret":"c"}""".toByteArray()))
    }

    @Test
    fun `an unknown variant reads as Unknown rather than failing the whole restore`() {
        val decoded = decodeCarriedConnection("""{"url":"https://a/","kind":"api_key","secret":"k","variant":"Plexseerr"}""".toByteArray())

        assertEquals(SeerrVariant.Unknown, decoded?.variant)
    }
}
