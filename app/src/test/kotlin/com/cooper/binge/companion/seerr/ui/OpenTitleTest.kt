package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTitleTest {
    @Test
    fun `a title goes to Binge by its link where Binge answers, else to the server's page`() {
        assertEquals("binge://title/movie/603", BingeHandOff.titleUri(RequestMediaType.Movie, 603))
        assertEquals("binge://title/tv/1396", BingeHandOff.titleUri(RequestMediaType.Tv, 1396))
        assertEquals(
            TitleTarget.Binge("binge://title/tv/1396"),
            BingeHandOff.target(bingeAnswers = true, RequestMediaType.Tv, 1396, "https://s/tv/1396"),
        )
        assertEquals(
            TitleTarget.Web("https://s/tv/1396"),
            BingeHandOff.target(bingeAnswers = false, RequestMediaType.Tv, 1396, "https://s/tv/1396"),
        )
    }

    @Test
    fun `a title with no tmdb id has no link, so it goes to the server's page`() {
        assertEquals(null, BingeHandOff.titleUri(RequestMediaType.Movie, 0))
        assertEquals(
            TitleTarget.Web("https://s/movie/0"),
            BingeHandOff.target(bingeAnswers = true, RequestMediaType.Movie, 0, "https://s/movie/0"),
        )
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
