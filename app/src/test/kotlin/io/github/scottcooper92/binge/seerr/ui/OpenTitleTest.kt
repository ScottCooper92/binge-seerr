package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
import org.junit.Assert.assertEquals
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
}
