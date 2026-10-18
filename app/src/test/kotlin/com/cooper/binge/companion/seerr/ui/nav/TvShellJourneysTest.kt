package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.DeepLinks
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""
private const val OVERVIEW = "Work-life balance."

/**
 * Journeys through the real television shell: `MainActivity`, `TvSeerrShell` and `TvConnectedShell` with the app's own
 * entries, ViewModels and stores. A request's link opens that request's page over Home, and the pure helper that reads the
 * link ([com.cooper.binge.companion.seerr.ui.DeepLinksTest]) says nothing of whether the shell follows it (#1597). Each
 * asserts on what is on screen, not on focus, which the shell's own focus tests own.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w960dp-h540dp-television-xhdpi")
class TvShellJourneysTest {
    @get:Rule
    internal val host = RealTvHostRule(this)

    private fun serveHome() {
        host.startServer()
        host.seerr.serve("GET /api/v1/request/count", """{"total":0,"movie":0,"tv":0,"pending":0,"processing":0}""")
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
        host.seerr.serve(
            "GET /api/v1/request/11",
            """{"id":11,"status":2,"createdAt":"2026-06-01T10:00:00.000Z","updatedAt":"2026-06-02T10:00:00.000Z",
               "requestedBy":{"id":7,"displayName":"scott"},"seasons":[{"seasonNumber":1,"status":3}],
               "media":{"id":900,"tmdbId":200,"mediaType":"tv","status":3}}""",
        )
        host.seerr.serve(
            "GET /api/v1/tv/200",
            """{"name":"Severance","overview":"$OVERVIEW","firstAirDate":"2022-02-18",
               "seasons":[{"seasonNumber":1,"name":"Season 1","episodeCount":9}]}""",
        )
    }

    private val home get() = host.string(R.string.tv_home)
    private val account get() = host.string(R.string.tv_account)

    @Test
    fun `a request's link, sent to the running app, opens that request's page over Home`() {
        serveHome()
        host.saveConnection()
        host.launch()
        host.awaitShowing(home)
        assertFalse("the page was open before the link", host.isShowing(OVERVIEW))

        host.sendLink(DeepLinks.request(11))

        host.awaitShowing(OVERVIEW)
    }

    @Test
    fun `a request's link in the launch intent opens that request's page once connected`() {
        serveHome()
        host.saveConnection()

        host.launch(link = DeepLinks.request(11))

        host.awaitShowing(OVERVIEW)
    }

    @Test
    fun `a link sent while no server is saved waits, and opens its page after the connection`() {
        serveHome()
        host.launch(link = DeepLinks.request(11))
        host.awaitShowing(host.string(R.string.tv_setup_code_headline))
        assertFalse("the page opened with no server", host.isShowing(OVERVIEW))

        host.saveConnection()

        host.awaitShowing(OVERVIEW)
    }

    @Test
    fun `links that are not a request's are drained, and the request's link behind them still opens`() {
        serveHome()
        host.saveConnection()
        host.launch()
        host.awaitShowing(home)

        host.sendLink(DeepLinks.issues())
        host.sendLink(DeepLinks.settings())
        host.sendLink(DeepLinks.reconnect())

        // A request's link behind them still opens, so the others were taken off the channel, not left in front of it.
        host.sendLink(DeepLinks.request(11))
        host.awaitShowing(OVERVIEW)
    }

    @Test
    fun `a request's link closes the sheet that was open and opens the page`() {
        serveHome()
        host.saveConnection()
        host.launch()
        host.awaitShowing(home)
        host.compose
            .onAllNodes(hasContentDescription(host.string(R.string.tv_account)))
            .onFirst()
            .performClick()
        host.awaitWorded(account)

        host.sendLink(DeepLinks.request(11))

        host.awaitShowing(OVERVIEW)
        assertFalse("the sheet stayed open", host.isWorded(account))
    }
}
