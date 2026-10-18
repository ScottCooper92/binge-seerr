package com.cooper.binge.companion.seerr.ui.handoff

import com.cooper.binge.companion.seerr.handoff.TitleReportLink
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val REQUEST_ONLY = 32
private const val MEDIA_ID = 77
private const val SERVER_ID = "3f1c2c9e-5a77-4d0e-9d65-0b1f4c2a8e10"
private const val OTHER_ID = "9a8b7c6d-1111-2222-3333-444455556666"
private const val TITLE = """{"title":"Heat","mediaInfo":{"id":$MEDIA_ID}}"""

/**
 * The phone's end of "Report an issue" from a TV (#1511): a code for this phone's own server opens the form, a code for
 * another server or a phone that is signed out says so and sends nothing, and a send goes to the connected server once.
 */
class ReportTitleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/movie/949", TITLE)
        seerr.serve("POST /api/v1/issue", "{}")
    }

    @After
    fun tearDown() = seerr.close()

    private fun here(
        mediaType: String = "movie",
        tmdbId: Int = 949,
        is4k: Boolean = false,
    ) = TitleReportLink(seerr.server.url("/").toString(), mediaType, tmdbId, is4k)

    private suspend fun TestScope.viewModel(
        link: TitleReportLink,
        connected: Boolean = true,
    ): ReportTitleViewModel {
        val connection = seerr.connection(this)
        if (!connected) connection.disconnect()
        return ReportTitleViewModel(connection, mainDispatcherRule.dispatcher, link)
    }

    private suspend fun ReportTitleViewModel.settled(): ReportTitleUiState = uiState.first { it !is ReportTitleUiState.Loading }

    @Test
    fun `a code for this phone's own server opens the form for the title, and sends nothing`() =
        runTest {
            val state = viewModel(here()).settled()

            assertEquals(ReportTitleUiState.Ready(title = "Heat", is4k = false, mediaId = MEDIA_ID), state)
            assertEquals(0, seerr.count("POST", "/api/v1/issue"))
        }

    @Test
    fun `the 4K mark on the code is kept for the form to say`() =
        runTest {
            assertTrue((viewModel(here(is4k = true)).settled() as ReportTitleUiState.Ready).is4k)
        }

    @Test
    fun `a code for another server says so, and neither reads nor sends anything`() =
        runTest {
            val other = TitleReportLink("https://other.example/", "movie", 949, is4k = false)

            val state = viewModel(other).settled()

            assertTrue(state is ReportTitleUiState.DifferentServer)
            assertEquals("other.example", (state as ReportTitleUiState.DifferentServer).theirs)
            assertEquals(0, seerr.count("GET", "/api/v1/movie/949"))
            assertEquals(0, seerr.count("POST", "/api/v1/issue"))
        }

    @Test
    fun `the same server reached by another address is the same server when both ends name it`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, settings = """{"plexClientIdentifier":"$SERVER_ID"}""")
            val overTheVpn = TitleReportLink("http://100.103.24.117:30042/", "movie", 949, is4k = false, serverId = SERVER_ID)

            val state = viewModel(overTheVpn).settled()

            assertEquals(ReportTitleUiState.Ready(title = "Heat", is4k = false, mediaId = MEDIA_ID), state)
        }

    @Test
    fun `a code naming another server by id is refused, and reporting anyway goes to this phone's own server on the user's tap`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN, settings = """{"plexClientIdentifier":"$SERVER_ID"}""")
            val other = here().copy(serverId = OTHER_ID)
            val vm = viewModel(other)

            assertTrue(vm.settled() is ReportTitleUiState.DifferentServer)
            assertEquals(0, seerr.count("GET", "/api/v1/movie/949"))

            vm.reportAnyway()

            assertEquals(
                ReportTitleUiState.Ready(title = "Heat", is4k = false, mediaId = MEDIA_ID),
                vm.uiState.first {
                    it is ReportTitleUiState.Ready
                },
            )
            assertEquals(0, seerr.count("POST", "/api/v1/issue"))
        }

    @Test
    fun `a server that names no id and another address leaves the choice to the user`() =
        runTest {
            val other = TitleReportLink("http://100.103.24.117:30042/", "movie", 949, is4k = false, serverId = OTHER_ID)
            val vm = viewModel(other)

            assertTrue(vm.settled() is ReportTitleUiState.DifferentServer)

            vm.reportAnyway()

            assertTrue(vm.uiState.first { it is ReportTitleUiState.Ready } is ReportTitleUiState.Ready)
        }

    @Test
    fun `reporting anyway does nothing unless the code was refused`() =
        runTest {
            val vm = viewModel(here())
            vm.settled()
            val before = seerr.count("GET", "/api/v1/movie/949")

            vm.reportAnyway()

            assertEquals(before, seerr.count("GET", "/api/v1/movie/949"))
        }

    @Test
    fun `a phone that is signed out says so`() =
        runTest {
            assertEquals(ReportTitleUiState.SignedOut, viewModel(here(), connected = false).settled())
            assertEquals(0, seerr.count("GET", "/api/v1/movie/949"))
        }

    @Test
    fun `a session the server rejects is signed out too`() =
        runTest {
            seerr.serve("GET /api/v1/movie/949", """{"message":"no"}""", code = 401)

            assertEquals(ReportTitleUiState.SignedOut, viewModel(here()).settled())
        }

    @Test
    fun `an account that may not report issues is told so`() =
        runTest {
            seerr.viewer(id = 1, permissions = REQUEST_ONLY)

            assertEquals(ReportTitleUiState.CannotReport, viewModel(here()).settled())
            assertEquals(0, seerr.count("GET", "/api/v1/movie/949"))
        }

    @Test
    fun `a title the server has no record of has nothing to report on`() =
        runTest {
            seerr.serve("GET /api/v1/movie/949", """{"title":"Heat"}""")

            assertEquals(ReportTitleUiState.NotTracked("Heat"), viewModel(here()).settled())
        }

    @Test
    fun `a show is looked up as a show`() =
        runTest {
            seerr.serve("GET /api/v1/tv/200", """{"name":"Severance","mediaInfo":{"id":5}}""")

            val state = viewModel(here(mediaType = "tv", tmdbId = 200)).settled()

            assertEquals(ReportTitleUiState.Ready(title = "Severance", is4k = false, mediaId = 5), state)
        }

    @Test
    fun `a title the server cannot find is an error, not a sign-in`() =
        runTest {
            seerr.serve("GET /api/v1/movie/949", """{"message":"gone"}""", code = 404)

            assertEquals(ReportTitleUiState.Error(SeerrError.NotFound), viewModel(here()).settled())
        }

    @Test
    fun `a send goes to the connected server once, with the message trimmed`() =
        runTest {
            val vm = viewModel(here())
            vm.settled()

            vm.send(IssueType.Subtitles, "  Out of sync by two seconds.  ")
            vm.uiState.first { (it as? ReportTitleUiState.Ready)?.report == IssueReport.Sent }
            vm.send(IssueType.Subtitles, "again")

            assertEquals(1, seerr.count("POST", "/api/v1/issue"))
            val body = seerr.body("POST", "/api/v1/issue")
            assertTrue(body, body.contains("\"mediaId\":$MEDIA_ID"))
            assertTrue(body, body.contains("Out of sync by two seconds."))
            assertTrue(body, !body.contains("  Out"))
        }

    @Test
    fun `a failed send says so and can be sent again`() =
        runTest {
            seerr.serve("POST /api/v1/issue", """{"message":"no"}""", code = 500)
            val vm = viewModel(here())
            vm.settled()

            vm.send(IssueType.Video, "Black screen.")
            vm.uiState.first { (it as? ReportTitleUiState.Ready)?.report is IssueReport.Failed }
            seerr.serve("POST /api/v1/issue", "{}")
            vm.send(IssueType.Video, "Black screen.")
            vm.uiState.first { (it as? ReportTitleUiState.Ready)?.report == IssueReport.Sent }

            assertEquals(2, seerr.count("POST", "/api/v1/issue"))
        }

    @Test
    fun `a blank message is not sent`() =
        runTest {
            val vm = viewModel(here())
            vm.settled()

            vm.send(IssueType.Other, "   ")

            assertEquals(0, seerr.count("POST", "/api/v1/issue"))
        }
}
