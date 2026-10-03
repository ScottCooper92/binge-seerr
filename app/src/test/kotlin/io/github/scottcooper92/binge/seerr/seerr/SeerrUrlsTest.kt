package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrUrlsTest {
    @Test
    fun `a private host defaults to http and a public one to https`() {
        assertEquals("http://192.168.1.10:5055/", "192.168.1.10:5055".normaliseBaseUrl())
        assertEquals("http://seerr.local/", "seerr.local".normaliseBaseUrl())
        assertEquals("http://nas/", "nas".normaliseBaseUrl())
        assertEquals("http://seerr.tail1234.ts.net/", "seerr.tail1234.ts.net".normaliseBaseUrl())
        assertEquals("https://seerr.example.com/", "seerr.example.com".normaliseBaseUrl())
    }

    @Test
    fun `an explicit scheme is kept, and a trailing slash added`() {
        assertEquals("http://seerr.example.com/", "http://seerr.example.com".normaliseBaseUrl())
        assertEquals("https://seerr.example.com/base/", "HTTPS://seerr.example.com/base".normaliseBaseUrl().lowercase())
    }

    @Test
    fun `cleartext to a public host is insecure, to a private one is not`() {
        assertEquals("seerr.example.com", "http://seerr.example.com".insecurePublicHostOrNull())
        assertEquals(null, "http://192.168.1.10:5055".insecurePublicHostOrNull())
        assertEquals(null, "https://seerr.example.com".insecurePublicHostOrNull())
    }

    @Test
    fun `home network names, loopback and link-local are local, and lookalikes are not`() {
        listOf(
            "seerr.lan",
            "seerr.home.arpa",
            "seerr.internal",
            "seerr.localhost",
            "127.0.1.1",
            "169.254.10.20",
            "fe80::1",
            "fd7a:115c:a1e0::1",
            "10.0.0.2",
            "nas",
        ).forEach { assertTrue(it, it.isLocalOrPrivateHost()) }
        listOf("127.example.com", "169.254.example.com", "seerr.lan.example.com", "internal.example.com")
            .forEach { assertFalse(it, it.isLocalOrPrivateHost()) }
    }

    @Test
    fun `the host to opt in for is the public one an http address names, lowercased`() {
        assertEquals("seerr.example.com", " HTTP://Seerr.Example.com:8080/ ".insecurePublicHostOrNull())
        assertEquals(null, "http://seerr.lan:5055".insecurePublicHostOrNull())
        assertEquals(null, "https://seerr.example.com".insecurePublicHostOrNull())
        assertEquals(null, "seerr.example.com".insecurePublicHostOrNull())
    }

    @Test
    fun `garbage is not a base url`() {
        assertFalse("http://".isValidBaseUrl())
        assertTrue("seerr.example.com".isValidBaseUrl())
    }

    @Test
    fun `a normalised url carries an explicit port only when one was typed`() {
        assertFalse("http://192.168.1.10/".hasExplicitPort())
        assertFalse("https://seerr.example.com/base/".hasExplicitPort())
        assertFalse("http://[::1]/".hasExplicitPort())
        assertTrue("http://192.168.1.10:5055/".hasExplicitPort())
        assertTrue("https://seerr.example.com:8443/base/".hasExplicitPort())
        assertTrue("http://[::1]:8080/".hasExplicitPort())
    }

    @Test
    fun `the default Seerr port is added to a portless normalised url`() {
        assertEquals("http://192.168.1.10:5055/", "http://192.168.1.10/".withDefaultSeerrPort())
        assertEquals("https://seerr.example.com:5055/base/", "https://seerr.example.com/base/".withDefaultSeerrPort())
    }
}
