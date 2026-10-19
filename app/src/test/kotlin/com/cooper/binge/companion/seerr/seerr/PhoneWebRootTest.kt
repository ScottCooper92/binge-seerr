package com.cooper.binge.companion.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Test

private const val BASE = "http://192.168.1.10:5055/"

/** The root a link for another device is written under: the server's Application URL where it is usable, else the address in use. */
class PhoneWebRootTest {
    private fun profile(applicationUrl: String?) =
        SeerrServerProfile(
            variant = SeerrVariant.Jellyseerr,
            version = SeerrVersion(3, 1, 0),
            settings = SeerrPublicSettings(applicationUrl = applicationUrl),
        )

    @Test
    fun `an application url is the root, ending in a slash`() {
        assertEquals("https://seerr.tail1234.ts.net/", profile("https://seerr.tail1234.ts.net").phoneWebRoot(BASE))
        assertEquals("https://seerr.tail1234.ts.net/", profile("https://seerr.tail1234.ts.net/").phoneWebRoot(BASE))
        assertEquals("http://100.64.0.7:5055/seerr/", profile("http://100.64.0.7:5055/seerr").phoneWebRoot(BASE))
    }

    @Test
    fun `no application url, or one that is not a web address, leaves the address in use`() {
        assertEquals(BASE, profile(null).phoneWebRoot(BASE))
        assertEquals(BASE, profile("").phoneWebRoot(BASE))
        assertEquals(BASE, profile("  ").phoneWebRoot(BASE))
        assertEquals(BASE, profile("seerr.example.com").phoneWebRoot(BASE))
        assertEquals(BASE, profile("ftp://seerr.example.com").phoneWebRoot(BASE))
        assertEquals(BASE, profile("javascript:alert(1)").phoneWebRoot(BASE))
        assertEquals(BASE, profile("https://").phoneWebRoot(BASE))
        assertEquals(BASE, profile("https://user:pass@seerr.example.com").phoneWebRoot(BASE))
        assertEquals(BASE, profile("http://bad host/").phoneWebRoot(BASE))
    }
}
