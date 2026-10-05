package io.github.scottcooper92.binge.seerr.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

private const val TOKEN = "AbC-_0123456789abcdefg"

/** The link from the TV's page to the phone app: built and read in one place, and refused unless it names a TV on the LAN. */
class TvHandOffLinksTest {
    private val target = TvHandOffTarget(host = "192.168.86.53", port = 41234, token = TOKEN)

    @Test
    fun `a link the TV builds reads back as the same target`() {
        val link = TvHandOffLinks.appLink(target)

        assertEquals("seerr-companion://tv-handoff?to=192.168.86.53:41234&token=$TOKEN", link)
        assertEquals(target, TvHandOffLinks.parse(link))
        assertEquals("http://192.168.86.53:41234/a/$TOKEN", target.url)
    }

    @Test
    fun `the intent url is pinned to the package and falls back to the page`() {
        val url = TvHandOffLinks.intentUrl(target, "io.example.app", fallbackUrl = target.url)

        assertTrue(url.startsWith("intent://tv-handoff?to=192.168.86.53:41234&token=$TOKEN#Intent;"))
        assertTrue(url.contains(";scheme=seerr-companion;"))
        assertTrue(url.contains(";package=io.example.app;"))
        assertTrue(url.endsWith(";end"))
        val fallback = url.substringAfter("S.browser_fallback_url=").substringBefore(';')
        assertEquals(target.url, URLDecoder.decode(fallback, "UTF-8"))
    }

    @Test
    fun `anything that is not a well-formed hand-off link reads as nothing`() {
        listOf(
            null,
            "",
            "seerr-companion://requests",
            "https://tv-handoff?to=192.168.1.2:80&token=$TOKEN",
            "seerr-companion://tv-handoff?to=192.168.1.2:80",
            "seerr-companion://tv-handoff?token=$TOKEN",
            "seerr-companion://tv-handoff?to=192.168.1.2&token=$TOKEN",
            "seerr-companion://tv-handoff?to=192.168.1.2:0&token=$TOKEN",
            "seerr-companion://tv-handoff?to=192.168.1.2:70000&token=$TOKEN",
            "seerr-companion://tv-handoff?to=192.168.1.2:80&token=short",
            "seerr-companion://tv-handoff?to=user@192.168.1.2:80&token=$TOKEN",
            "seerr-companion://tv-handoff?to=192.168.1.2/x:80&token=$TOKEN",
            "seerr-companion://tv-handoff?to=%zz&token=$TOKEN",
            "not a uri at all",
        ).forEach { assertNull(it, TvHandOffLinks.parse(it)) }
    }

    @Test
    fun `only a private IPv4 address that is not this phone counts as a TV on the LAN`() {
        listOf("192.168.1.20", "10.0.0.5", "172.16.4.1", "169.254.10.10").forEach {
            assertTrue(it, target.copy(host = it).isOnLan)
        }
        listOf(
            "8.8.8.8",
            "127.0.0.1",
            "seerr.lan",
            "tv",
            "evil.example.com",
            "192.168.1",
            "192.168.1.300",
            "fd00::1",
            "100.64.0.1",
            "100.100.100.100",
        ).forEach { assertFalse(it, target.copy(host = it).isOnLan) }
    }

    @Test
    fun `a token is 128 random bits, url-safe, and fresh each time`() {
        val token = newHandOffToken()

        assertTrue(Regex("[A-Za-z0-9_-]{22}").matches(token))
        assertNotEquals(token, newHandOffToken())
    }
}
