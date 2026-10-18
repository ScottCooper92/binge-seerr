package com.cooper.binge.companion.seerr.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The code a TV shows for "Report an issue", and the phone's reading of it (#1511). */
class TitleReportLinksTest {
    @Test
    fun `a title's page on the server reads as that title on that server`() {
        val link = TitleReportLinks.parse("https://seerr.example/movie/900")

        assertEquals(TitleReportLink("https://seerr.example/", "movie", 900, is4k = false), link)
        assertEquals("tv", TitleReportLinks.parse("http://192.168.1.10:5055/tv/200")?.mediaType)
    }

    @Test
    fun `a server under a path keeps it, so the comparison is against the whole address`() {
        val link = TitleReportLinks.parse("https://example.com/seerr/tv/200")

        assertEquals("https://example.com/seerr/", link?.server)
        assertEquals(200, link?.tmdbId)
    }

    @Test
    fun `the 4K mark is the one query it carries`() {
        assertTrue(TitleReportLinks.parse("https://seerr.example/movie/900?is4k=1")?.is4k == true)
        assertFalse(TitleReportLinks.parse("https://seerr.example/movie/900?is4k=0")?.is4k == true)
    }

    @Test
    fun `what the TV writes is what the phone reads`() {
        val written = TitleReportLinks.write("https://seerr.example/tv/200", is4k = true)

        assertEquals(TitleReportLink("https://seerr.example/", "tv", 200, is4k = true), TitleReportLinks.parse(written))
        assertEquals("https://seerr.example/tv/200", TitleReportLinks.write("https://seerr.example/tv/200", is4k = false))
    }

    @Test
    fun `anything that is not a title's page is ignored`() {
        listOf(
            null,
            "",
            "not a url",
            "ftp://seerr.example/movie/900",
            "https://seerr.example/",
            "https://seerr.example/movie/",
            "https://seerr.example/movie/abc",
            "https://seerr.example/movie/0",
            "https://seerr.example/movie/-4",
            "https://seerr.example/person/900",
            "https://user:pw@seerr.example/movie/900",
            "https://seerr.example/movie/900?token=abc",
            "https://seerr.example/movie/900?is4k=1&x=2",
            "http://192.168.1.20:41234/a/abcdefgh",
        ).forEach { assertNull(it, TitleReportLinks.parse(it)) }
    }

    @Test
    fun `two spellings of one server are the same server`() {
        val link = TitleReportLinks.parse("https://Seerr.Example:443/movie/900")!!

        assertTrue(TitleReportLinks.isSameServer(link, "https://seerr.example"))
        assertTrue(TitleReportLinks.isSameServer(link, "https://seerr.example/"))
    }

    @Test
    fun `another host, port, scheme or path is another server`() {
        val link = TitleReportLinks.parse("https://seerr.example/movie/900")!!

        assertFalse(TitleReportLinks.isSameServer(link, "https://other.example/"))
        assertFalse(TitleReportLinks.isSameServer(link, "https://seerr.example:8443/"))
        assertFalse(TitleReportLinks.isSameServer(link, "http://seerr.example/"))
        assertFalse(TitleReportLinks.isSameServer(link, "https://seerr.example/seerr/"))
        assertFalse(TitleReportLinks.isSameServer(link, ""))
    }
}
