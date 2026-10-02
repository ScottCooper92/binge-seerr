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

    private fun withForgotPassword(url: String?) = jellyfin("").copy(forgotPasswordUrl = url)

    @Test
    fun `a trailing slash on the Jellyfin external url shows the error and blocks saving`() {
        listOf("https://jf.example.com/", "http://jf.lan:8096/jf/", " https://jf.example.com/ ").forEach {
            assertFalse(it, jellyfin(it).externalUrlValid)
            assertFalse(it, jellyfin(it).valid)
        }
    }

    @Test
    fun `Plex and Tautulli keep accepting a trailing slash, as their web forms do`() {
        assertTrue(plex("https://plex.example.com/").valid)
        assertTrue(tautulli("https://tautulli.example.com/").valid)
    }

    @Test
    fun `a blank or absent forgot password url is allowed`() {
        listOf(null, "", "   ").forEach { assertTrue(it.orEmpty(), withForgotPassword(it).valid) }
    }

    @Test
    fun `a web address without a trailing slash is a valid forgot password url`() {
        listOf("https://jf.example.com/reset", "  HTTP://jf.lan/reset  ").forEach {
            assertTrue(it, withForgotPassword(it).forgotPasswordUrlValid)
            assertTrue(it, withForgotPassword(it).valid)
        }
    }

    @Test
    fun `a forgot password url that is not a web address or ends in a slash blocks saving`() {
        (notWebAddresses + "https://jf.example.com/reset/").forEach {
            assertFalse(it, withForgotPassword(it).forgotPasswordUrlValid)
            assertFalse(it, withForgotPassword(it).valid)
        }
    }

    @Test
    fun `a bad forgot password url does not flag the external url`() {
        assertTrue(withForgotPassword("nope").externalUrlValid)
    }
}
