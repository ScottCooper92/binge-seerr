package io.github.scottcooper92.binge.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.REQUEST
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val MANAGE_ISSUES = 1 shl 20

private fun requestsPage(
    ids: List<Int>,
    pages: Int = 1,
): String =
    ids.joinToString(",", prefix = """{"pageInfo":{"pages":$pages,"results":${ids.size}},"results":[""", postfix = "]}") { id ->
        """{"id":$id,"status":1,"media":{"tmdbId":${100 + id},"mediaType":"movie","status":2}}"""
    }

private fun ownRequests(vararg rows: Pair<Int, String>): String =
    rows.joinToString(",", prefix = """{"pageInfo":{"pages":1,"results":${rows.size}},"results":[""", postfix = "]}") { (id, state) ->
        """{"id":$id,$state,"media":{"tmdbId":${100 + id},"mediaType":"movie"${state.substringAfter("|", "")}}}"""
            .replace("|", "")
    }

/** The poll over a scripted server and a real DataStore; the notifier is a fake that records. */
class NotificationsCheckerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val notifier = FakeNotifier()
    private var stores = 0

    @Before
    fun setUp() {
        seerr.start()
        seerr.viewer(id = 7, permissions = ADMIN)
        seerr.serve("GET /api/v1/request", requestsPage(emptyList()))
        seerr.serve("GET /api/v1/issue", """{"pageInfo":{"pages":1,"results":0},"results":[]}""")
        seerr.serve("GET /api/v1/user/7/requests", requestsPage(emptyList()))
        seerr.serve("GET /api/v1/movie/101", """{"title":"Heat"}""")
        seerr.serve("GET /api/v1/movie/102", """{"title":"Ronin"}""")
        seerr.serve("GET /api/v1/movie/103", """{"title":"Thief"}""")
    }

    @After
    fun tearDown() = seerr.close()

    private fun TestScope.prefs(): NotificationPrefs =
        NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("n${stores++}.preferences_pb") })

    private suspend fun TestScope.checker(
        prefs: NotificationPrefs,
        vararg on: NotificationSignal,
    ): NotificationsChecker {
        on.forEach { prefs.setEnabled(it, true) }
        val connection = seerr.connection(this)
        return NotificationsChecker(prefs, connection, NotificationFeeds(connection, TitleCache()), notifier)
    }

    @Test
    fun `nothing runs while every signal is off, or while nothing could be shown`() =
        runTest {
            val prefs = prefs()
            assertEquals(CheckResult.Ok, checker(prefs).check())
            notifier.canPost = false
            assertEquals(CheckResult.Ok, checker(prefs, NotificationSignal.PendingRequests).check())
            assertTrue(seerr.received.none { it.url.encodedPath == "/api/v1/request" })
        }

    @Test
    fun `a feed seeds silently, then announces what is newer than the cursor across pages, and advances`() =
        runTest {
            val prefs = prefs()
            val checker = checker(prefs, NotificationSignal.PendingRequests)
            seerr.serve("GET /api/v1/request", requestsPage(listOf(3, 2, 1)))

            assertEquals(CheckResult.Ok, checker.check())
            assertTrue(notifier.newRequests.isEmpty())
            assertEquals(3, prefs.cursor(NotificationSignal.PendingRequests))

            seerr.serve("GET /api/v1/request", requestsPage(listOf(5, 4, 3), pages = 2))
            assertEquals(CheckResult.Ok, checker.check())
            assertEquals(listOf(listOf(5, 4)), notifier.newRequests)
            assertEquals(5, prefs.cursor(NotificationSignal.PendingRequests))

            assertEquals(CheckResult.Ok, checker.check())
            assertEquals(1, notifier.newRequests.size)
        }

    @Test
    fun `an empty feed seeds to zero, so the first row ever is announced rather than seeding again`() =
        runTest {
            val prefs = prefs()
            val checker = checker(prefs, NotificationSignal.PendingRequests)
            checker.check()
            assertEquals(0, prefs.cursor(NotificationSignal.PendingRequests))

            seerr.serve("GET /api/v1/request", requestsPage(listOf(1)))
            checker.check()
            assertEquals(listOf(listOf(1)), notifier.newRequests)
        }

    @Test
    fun `the feeds follow the viewer's permissions, and a failed fetch leaves the cursor alone`() =
        runTest {
            seerr.viewer(id = 7, permissions = MANAGE_ISSUES)
            val prefs = prefs()
            val checker = checker(prefs, NotificationSignal.PendingRequests, NotificationSignal.OpenIssues)
            seerr.serve(
                "GET /api/v1/issue",
                """{"pageInfo":{"pages":1,"results":1},"results":[{"id":9,"media":{"tmdbId":101,"mediaType":"movie"}}]}""",
            )

            assertEquals(CheckResult.Ok, checker.check())
            assertTrue(seerr.received.none { it.url.encodedPath == "/api/v1/request" })
            assertEquals(9, prefs.cursor(NotificationSignal.OpenIssues))
            assertNull(prefs.cursor(NotificationSignal.PendingRequests))

            seerr.serve("GET /api/v1/issue", "", code = 500)
            assertEquals(CheckResult.TransientFailure, checker.check())
            assertEquals(9, prefs.cursor(NotificationSignal.OpenIssues))

            seerr.serve("GET /api/v1/issue", "", code = 401)
            assertEquals(CheckResult.AuthFailure, checker.check())
        }

    @Test
    fun `an own request is announced once on entering a state, and again if it leaves and comes back`() =
        runTest {
            val prefs = prefs()
            val checker = checker(prefs, NotificationSignal.RequestApproved, NotificationSignal.RequestAvailable)
            seerr.serve("GET /api/v1/user/7/requests", ownRequests(1 to """"status":2""", 2 to """"status":1"""))

            checker.check()
            assertTrue(notifier.approved.isEmpty())
            assertEquals(setOf(1), prefs.notifiedIds(NotificationSignal.RequestApproved))

            seerr.serve(
                "GET /api/v1/user/7/requests",
                ownRequests(1 to """"status":2|,"status":5""", 2 to """"status":2""", 3 to """"status":3"""),
            )
            checker.check()
            assertEquals(listOf(listOf(2)), notifier.approved)
            assertEquals(listOf(listOf(1)), notifier.available)
            assertTrue(notifier.declined.isEmpty())

            seerr.serve("GET /api/v1/user/7/requests", ownRequests(2 to """"status":1"""))
            checker.check()
            seerr.serve("GET /api/v1/user/7/requests", ownRequests(2 to """"status":2"""))
            checker.check()
            assertEquals(listOf(listOf(2), listOf(2)), notifier.approved)
        }

    @Test
    fun `a plain user's own signals run without the moderator feeds`() =
        runTest {
            seerr.viewer(id = 7, permissions = REQUEST)
            val checker = checker(prefs(), NotificationSignal.PendingRequests, NotificationSignal.RequestDeclined)
            assertEquals(CheckResult.Ok, checker.check())
            assertTrue(seerr.received.none { it.url.encodedPath == "/api/v1/request" })
            assertTrue(seerr.received.any { it.url.encodedPath == "/api/v1/user/7/requests" })
        }
}
