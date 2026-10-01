package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DvrFormExternalUrlTest {
    private fun form(externalUrl: String) =
        DvrForm
            .blank(ServiceType.Radarr)
            .copy(
                name = "Movies",
                host = "radarr.local",
                apiKey = "key",
                profileId = 1,
                rootFolder = "/movies",
                externalUrl = externalUrl,
            )

    @Test
    fun `a blank external url is allowed`() {
        assertTrue(form("").valid)
        assertTrue(form("   ").valid)
    }

    @Test
    fun `http and https addresses are allowed, with surrounding spaces and any case`() {
        assertTrue(form("https://radarr.example.com").valid)
        assertTrue(form("http://192.168.1.5:7878/radarr").valid)
        assertTrue(form("  HTTPS://Radarr.example.com  ").valid)
    }

    @Test
    fun `text that is not a web address shows the error and blocks saving`() {
        listOf("radarr.example.com", "ftp://radarr.example.com", "htp://radarr", "/radarr", "https:/x").forEach {
            val f = form(it)
            assertFalse(it, f.externalUrlValid)
            assertFalse(it, f.valid)
        }
    }
}
