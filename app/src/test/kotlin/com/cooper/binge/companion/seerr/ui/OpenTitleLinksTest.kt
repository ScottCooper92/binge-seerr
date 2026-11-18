package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.ui.requests.RequestOpenLink
import com.cooper.binge.companion.seerr.ui.requests.rememberRequestOpenLinks
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * With discovery on a title opens its own page from a request: the open sheet's first row goes through [LocalOpenTitle], and the
 * hand-off rows stay beneath it. With discovery off the sheet is what it was.
 */
@RunWith(RobolectricTestRunner::class)
class OpenTitleLinksTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private val opened = mutableListOf<Triple<TitleKind, Int, String>>()

    private fun links(
        detail: RequestDetail,
        discovery: Boolean,
    ): List<RequestOpenLink> {
        var links = emptyList<RequestOpenLink>()
        compose.setContent {
            SeerrTheme {
                CompositionLocalProvider(
                    LocalDiscoveryEnabled provides discovery,
                    LocalOpenTitle provides { kind, id, url -> opened += Triple(kind, id, url) },
                ) { links = rememberRequestOpenLinks(detail) }
            }
        }
        compose.waitForIdle()
        return links
    }

    private fun film(webUrl: String) =
        requestDetail().let { it.copy(item = it.item.copy(mediaType = RequestMediaType.Movie), webUrl = webUrl) }

    private fun series(webUrl: String) = film(webUrl).let { it.copy(item = it.item.copy(mediaType = RequestMediaType.Tv)) }

    @Test
    fun `with discovery on the open sheet leads with the title's own page and keeps the hand-off beneath it`() {
        val detail = film("https://seerr.example.com/movie/1")
        val labels = links(detail, discovery = true).map { it.label }

        assertEquals(string(R.string.request_open_title), labels.first())
        assertEquals(2, labels.size)
    }

    @Test
    fun `only the title's own row stays in the app, so only it takes the chevron`() {
        val inApp = links(film("https://seerr.example.com/movie/1"), discovery = true).map { it.inApp }
        assertEquals(listOf(true, false), inApp)
    }

    @Test
    fun `the title row opens a film as a film`() {
        val film = film("https://seerr.example.com/movie/1")
        links(film, discovery = true).first().onOpen()
        assertEquals(Triple(TitleKind.Movie, film.item.tmdbId, film.webUrl), opened.single())
    }

    @Test
    fun `a show opens as a series`() {
        val show = series("https://seerr.example.com/tv/1")
        links(show, discovery = true).first().onOpen()
        assertEquals(TitleKind.Series, opened.single().first)
    }

    @Test
    fun `with discovery on the title still opens where the server's address cannot be a real site`() {
        val labels = links(film("https://demo.seerr.invalid/movie/1"), discovery = true).map { it.label }
        assertEquals(listOf(string(R.string.request_open_title)), labels)
    }

    @Test
    fun `with discovery off the open sheet has no row for the title's own page`() {
        val labels = links(film("https://seerr.example.com/movie/1"), discovery = false).map { it.label }
        assertFalse(string(R.string.request_open_title) in labels)
        assertEquals(1, labels.size)
    }

    @Test
    fun `a request's media type maps to the kind of page it opens`() {
        assertEquals(TitleKind.Movie, RequestMediaType.Movie.toTitleKind())
        assertEquals(TitleKind.Series, RequestMediaType.Tv.toTitleKind())
        assertEquals(RequestMediaType.Movie, TitleKind.Movie.toRequestMediaType())
        assertEquals(RequestMediaType.Tv, TitleKind.Series.toRequestMediaType())
    }
}
