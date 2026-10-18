package com.cooper.binge.companion.seerr.seerr

import com.binge.companion.contracts.v1.MediaType
import com.cooper.binge.companion.seerr.ui.BingeHandOff
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The app's own media type + TMDB id as the contract's `MediaId`, the one translation every call site goes through (#1659). */
class SeerrMediaIdsTest {
    @Test
    fun `a movie and a show are the contract's two media types`() {
        assertEquals(MediaType.MEDIA_TYPE_MOVIE, mediaIdOf(RequestMediaType.Movie, 603)?.mediaType)
        assertEquals(MediaType.MEDIA_TYPE_TV, mediaIdOf(RequestMediaType.Tv, 1396)?.mediaType)
        assertEquals(603, mediaIdOf(RequestMediaType.Movie, 603)?.tmdbId)
    }

    @Test
    fun `a TMDB id that is not positive names nothing`() {
        listOf(0, -1, Int.MIN_VALUE).forEach { id ->
            assertNull(mediaIdOf(RequestMediaType.Movie, id))
            assertNull(mediaIdOf(RequestMediaType.Tv, id))
        }
    }

    @Test
    fun `the hand-off link is built from the same id, and from none where there is none`() {
        assertEquals("binge://title/tv/1396", BingeHandOff.titleUri(RequestMediaType.Tv, 1396))
        assertEquals("binge://title/movie/603", BingeHandOff.titleUri(RequestMediaType.Movie, 603))
        assertNull(BingeHandOff.titleUri(RequestMediaType.Tv, 0))
    }
}
