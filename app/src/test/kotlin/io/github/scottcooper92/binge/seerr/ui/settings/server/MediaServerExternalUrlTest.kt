package io.github.scottcooper92.binge.seerr.ui.settings.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaServerExternalUrlTest {
    private val notWebAddresses = listOf("jellyfin.example.com", "ftp://jellyfin.example.com", "htp://jf", "/web", "https:/x")

    private fun plex(externalUrl: String) =
        MediaServerForm(kind = MediaServerKind.Plex, host = "plex.lan", port = "32400", externalUrl = externalUrl)

    private fun jellyfin(externalUrl: String) =
        MediaServerForm(kind = MediaServerKind.Jellyfin, host = "jf.lan", port = "8096", externalUrl = externalUrl)

    private fun tautulli(externalUrl: String) =
        TautulliForm(host = "tautulli.lan", port = "8181", apiKey = "key", externalUrl = externalUrl)

    @Test
    fun `a blank external url is allowed on every form`() {
        listOf("", "   ").forEach {
            assertTrue(plex(it).valid)
            assertTrue(jellyfin(it).valid)
            assertTrue(tautulli(it).valid)
        }
    }

    @Test
    fun `http and https addresses are allowed, with surrounding spaces and any case`() {
        listOf("https://jf.example.com", "http://192.168.1.5:8096/jf", "  HTTPS://Jf.example.com  ").forEach {
            assertTrue(it, plex(it).valid)
            assertTrue(it, jellyfin(it).valid)
            assertTrue(it, tautulli(it).valid)
        }
    }

    @Test
    fun `text that is not a web address shows the error and blocks saving, for the media server`() {
        notWebAddresses.forEach {
            assertFalse(it, plex(it).externalUrlValid)
            assertFalse(it, plex(it).valid)
            assertFalse(it, jellyfin(it).externalUrlValid)
            assertFalse(it, jellyfin(it).valid)
        }
    }

    @Test
    fun `text that is not a web address shows the error and blocks saving, for Tautulli`() {
        notWebAddresses.forEach {
            assertFalse(it, tautulli(it).externalUrlValid)
            assertFalse(it, tautulli(it).valid)
        }
    }
}
