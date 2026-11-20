package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatuses
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

private const val TMDB = 603

private fun row(
    mediaStatus: SeerrMediaStatusCode?,
    is4k: Boolean = false,
    mediaType: RequestMediaType = RequestMediaType.Movie,
) = RequestItem(
    id = 1,
    tmdbId = TMDB,
    mediaType = mediaType,
    title = "The Matrix",
    posterUrl = null,
    year = null,
    requestedBy = null,
    requestedById = null,
    requestedAtMillis = null,
    status = null,
    mediaStatus = mediaStatus,
    download = null,
    seasonNumbers = emptyList(),
    is4k = is4k,
)

private fun held(
    standard: SeerrMediaStatusCode?,
    fourK: SeerrMediaStatusCode? = null,
    type: TitleType = TitleType.Movie,
) = mapOf(TitleKey(type, TMDB) to TitleStatuses(standard = standard, fourK = fourK))

class RequestStatusOverlayTest {
    @Test
    fun `a title blocked elsewhere shows blocked on its request row`() {
        val shown = row(SeerrMediaStatusCode.Pending).withBlockFrom(held(SeerrMediaStatusCode.Blocklisted))

        assertEquals(SeerrMediaStatusCode.Blocklisted, shown.mediaStatus)
    }

    @Test
    fun `a title unblocked elsewhere is no longer shown blocked`() {
        val shown = row(SeerrMediaStatusCode.Blocklisted).withBlockFrom(held(standard = null))

        assertEquals(null, shown.mediaStatus)
    }

    @Test
    fun `a status other than a block is the list's own to say`() {
        val pending = row(SeerrMediaStatusCode.Processing)

        assertSame(pending, pending.withBlockFrom(held(SeerrMediaStatusCode.Pending)))
    }

    @Test
    fun `a row for a title the store has not seen is left alone`() {
        val pending = row(SeerrMediaStatusCode.Pending)

        assertSame(pending, pending.withBlockFrom(emptyMap()))
    }

    @Test
    fun `a film and a show with one id are told apart`() {
        val show = row(SeerrMediaStatusCode.Pending, mediaType = RequestMediaType.Tv)

        assertSame(show, show.withBlockFrom(held(SeerrMediaStatusCode.Blocklisted, type = TitleType.Movie)))
    }

    @Test
    fun `a 4K row follows the 4K version`() {
        val blocked = held(standard = null, fourK = SeerrMediaStatusCode.Blocklisted)
        val shown = row(SeerrMediaStatusCode.Pending, is4k = true).withBlockFrom(blocked)

        assertEquals(SeerrMediaStatusCode.Blocklisted, shown.mediaStatus)
    }

    @Test
    fun `a 4K row blocked by the server is not unblocked by a store with no 4K status`() {
        val blocked = row(SeerrMediaStatusCode.Blocklisted, is4k = true)

        assertSame(blocked, blocked.withBlockFrom(held(standard = null)))
    }
}
