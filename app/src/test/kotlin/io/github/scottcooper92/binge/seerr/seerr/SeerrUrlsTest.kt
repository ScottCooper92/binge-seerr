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
        assertTrue("http://seerr.example.com".isInsecurePublicUrl())
        assertFalse("http://192.168.1.10:5055".isInsecurePublicUrl())
        assertFalse("https://seerr.example.com".isInsecurePublicUrl())
    }

    @Test
    fun `garbage is not a base url`() {
        assertFalse("http://".isValidBaseUrl())
        assertTrue("seerr.example.com".isValidBaseUrl())
    }
}
