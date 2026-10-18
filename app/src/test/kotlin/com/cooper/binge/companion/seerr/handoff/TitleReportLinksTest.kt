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

        assertTrue(TitleReportLinks.isSameServer(link, "https://seerr.example", connectedId = null))
        assertTrue(TitleReportLinks.isSameServer(link, "https://seerr.example/", connectedId = null))
    }

    @Test
    fun `another host, port, scheme or path is another server`() {
        val link = TitleReportLinks.parse("https://seerr.example/movie/900")!!

        assertFalse(TitleReportLinks.isSameServer(link, "https://other.example/", connectedId = null))
        assertFalse(TitleReportLinks.isSameServer(link, "https://seerr.example:8443/", connectedId = null))
        assertFalse(TitleReportLinks.isSameServer(link, "http://seerr.example/", connectedId = null))
        assertFalse(TitleReportLinks.isSameServer(link, "https://seerr.example/seerr/", connectedId = null))
        assertFalse(TitleReportLinks.isSameServer(link, "", connectedId = null))
    }

    @Test
    fun `the server's id rides in the code and is read back, and a code without one still reads`() {
        val written = TitleReportLinks.write("https://seerr.example/movie/900", is4k = true, serverId = ID)

        assertEquals(TitleReportLink("https://seerr.example/", "movie", 900, is4k = true, serverId = ID), TitleReportLinks.parse(written))
        assertEquals("https://seerr.example/movie/900?sid=$ID", TitleReportLinks.write("https://seerr.example/movie/900", false, ID))
        assertNull(TitleReportLinks.parse("https://seerr.example/movie/900")?.serverId)
    }

    @Test
    fun `an id that is not the shape of one is dropped and the code still reads`() {
        listOf("", "a b", "x".repeat(65), "id;rm", "%E2%9C%93").forEach { bad ->
            val link = TitleReportLinks.parse("https://seerr.example/movie/900?sid=$bad")
            assertEquals(bad, 900, link?.tmdbId)
            assertNull(bad, link?.serverId)
        }
        assertEquals("https://seerr.example/movie/900", TitleReportLinks.write("https://seerr.example/movie/900", false, "not an id"))
    }

    @Test
    fun `two known ids decide it, whatever the addresses say`() {
        val link = TitleReportLinks.parse("http://192.168.86.38:30042/movie/900?sid=$ID")!!

        assertTrue(TitleReportLinks.isSameServer(link, "http://100.103.24.117:30042/", ID))
        assertFalse(TitleReportLinks.isSameServer(link, "http://192.168.86.38:30042/", OTHER_ID))
    }

    @Test
    fun `only two known and different ids make it another server for certain`() {
        val link = TitleReportLinks.parse("https://seerr.example/movie/900?sid=$ID")!!

        assertTrue(TitleReportLinks.isOtherServerById(link, OTHER_ID))
        assertFalse(TitleReportLinks.isOtherServerById(link, ID))
        assertFalse(TitleReportLinks.isOtherServerById(link, null))
        assertFalse(TitleReportLinks.isOtherServerById(TitleReportLinks.parse("https://seerr.example/movie/900")!!, OTHER_ID))
    }

    @Test
    fun `with an id on only one side the addresses decide`() {
        val withId = TitleReportLinks.parse("https://seerr.example/movie/900?sid=$ID")!!
        val without = TitleReportLinks.parse("https://seerr.example/movie/900")!!

        assertTrue(TitleReportLinks.isSameServer(withId, "https://seerr.example/", connectedId = null))
        assertFalse(TitleReportLinks.isSameServer(withId, "https://other.example/", connectedId = null))
        assertTrue(TitleReportLinks.isSameServer(without, "https://seerr.example/", ID))
        assertFalse(TitleReportLinks.isSameServer(without, "https://other.example/", ID))
    }
}

private const val ID = "3f1c2c9e-5a77-4d0e-9d65-0b1f4c2a8e10"
private const val OTHER_ID = "9a8b7c6d-1111-2222-3333-444455556666"
