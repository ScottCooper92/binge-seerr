package com.cooper.binge.companion.seerr.seerr

import com.cooper.homelab.core.server.network.isLocalOrPrivateHost
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
    fun `the insecure host is in the spelling the cleartext guard sees on the request`() {
        assertEquals("xn--caf-dma.example", "http://café.example".insecurePublicHostOrNull())
        assertEquals("2001:4860::8888", "http://[2001:4860:0:0::8888]".insecurePublicHostOrNull())
        assertEquals("8.8.8.8", "http://[::ffff:8.8.8.8]".insecurePublicHostOrNull())
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
        ).forEach { assertTrue(it, isLocalOrPrivateHost(it)) }
        listOf(
            "127.example.com",
            "169.254.example.com",
            "10.example.com",
            "192.168.example.com",
            "172.16.example.com",
            "100.64.example.com",
            "seerr.lan.example.com",
            "internal.example.com",
        ).forEach { assertFalse(it, isLocalOrPrivateHost(it)) }
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
    fun `each fork's contribute link matches its own About page`() {
        assertEquals("https://github.com/sponsors/sct", SeerrVariant.Jellyseerr.contributeUrl())
        assertEquals("https://opencollective.com/seerr", SeerrVariant.Seerr.contributeUrl())
        assertEquals("https://github.com/sponsors/sct", SeerrVariant.Overseerr.contributeUrl())
    }

    @Test
    fun `each project's release notes are its own releases page, Jellyseerr's now Seerr's`() {
        assertEquals("https://github.com/seerr-team/seerr/releases", SeerrVariant.Seerr.releaseNotesUrl())
        assertEquals("https://github.com/seerr-team/seerr/releases", SeerrVariant.Jellyseerr.releaseNotesUrl())
        assertEquals("https://github.com/sct/overseerr/releases", SeerrVariant.Overseerr.releaseNotesUrl())
        assertEquals("https://github.com/seerr-team/seerr/releases", SeerrVariant.Unknown.releaseNotesUrl())
    }

    @Test
    fun `only an http or https address that can be a real site is openable`() {
        assertTrue(isOpenableSite("https://seerr.example.com/movie/603"))
        assertTrue(isOpenableSite("http://192.168.1.20:5055/tv/1396"))
        assertFalse(isOpenableSite("https://demo.seerr.invalid/movie/603"))
        assertFalse(isOpenableSite("https://invalid/movie/603"))
        assertFalse(isOpenableSite("ftp://seerr.example.com/movie/603"))
        assertFalse(isOpenableSite("binge://title/movie/603"))
        assertFalse(isOpenableSite("not a url"))
        assertFalse(isOpenableSite(""))
    }
}
