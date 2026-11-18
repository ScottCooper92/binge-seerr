package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.telemetry.DiscardLogger
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import com.cooper.binge.companion.seerr.ui.users.settings.ScriptedSeerr
import com.cooper.binge.companion.seerr.util.FakeTitleDao
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.notifications.DataStoreFeedCursorStore
import com.cooper.homelab.core.notifications.FeedCursorKey
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

private val SERVER = ServerId("server-1")
private val PENDING_KEY = FeedCursorKey(SERVER, "7", "pending_requests")

private fun requestsPage(ids: List<Int>): String =
    ids.joinToString(",", prefix = """{"pageInfo":{"pages":1,"results":${ids.size}},"results":[""", postfix = "]}") { id ->
        """{"id":$id,"status":1,"media":{"tmdbId":${100 + id},"mediaType":"movie","status":2}}"""
    }

/**
 * The poll on the shared library's cursor store: the same checker over the same scripted server, with each cursor kept per
 * server and user. The cursors the released app wrote are read once, and what changes a signal's cursor is unchanged.
 */
class SharedPollingTest {
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
        (1..9).forEach { seerr.serve("GET /api/v1/movie/${100 + it}", """{"title":"Movie $it"}""") }
    }

    @After
    fun tearDown() = seerr.close()

    private class Wiring(
        val data: DataStore<Preferences>,
        val prefs: NotificationPrefs,
        val store: DataStoreFeedCursorStore,
        val checker: NotificationsChecker,
    )

    private suspend fun TestScope.wiring(data: DataStore<Preferences> = newStore()): Wiring {
        val store = DataStoreFeedCursorStore(data, "feed_cursor")
        val pollUser = PollUserStore(data)
        val prefs = NotificationPrefs(data, SharedCursorReset(store, { SERVER }, pollUser))
        val connection = seerr.connection(this)
        val feeds = NotificationFeeds(connection, TitleCache(FakeTitleDao()), DiscardLogger)
        val cursors = SharedCursorFeeds(prefs, store, { SERVER }, pollUser, DiscardLogger)
        return Wiring(data, prefs, store, NotificationsChecker(prefs, connection, feeds, notifier, cursors))
    }

    private fun TestScope.newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("n${stores++}.preferences_pb") }

    @Test
    fun `a feed seeds silently, announces what is newer, and advances in the shared store`() =
        runTest {
            val w = wiring()
            w.prefs.setEnabled(NotificationSignal.PendingRequests, true)
            seerr.serve("GET /api/v1/request", requestsPage(listOf(3, 2, 1)))

            assertEquals(CheckResult.Ok, w.checker.check())
            assertTrue(notifier.newRequests.isEmpty())
            assertEquals("3", w.store.get(PENDING_KEY))

            seerr.serve("GET /api/v1/request", requestsPage(listOf(5, 4, 3)))
            assertEquals(CheckResult.Ok, w.checker.check())
            assertEquals(listOf(listOf(5, 4)), notifier.newRequests)
            assertEquals("5", w.store.get(PENDING_KEY))
            assertNull(w.prefs.cursor(NotificationSignal.PendingRequests))
        }

    @Test
    fun `the cursor the released app wrote is read once, so nothing is announced twice or missed`() =
        runTest {
            val data = newStore()
            data.edit { it[intPreferencesKey("signal_pending_requests_cursor")] = 3 }
            val w = wiring(data)
            w.prefs.setEnabled(NotificationSignal.PendingRequests, true)
            data.edit { it[intPreferencesKey("signal_pending_requests_cursor")] = 3 }
            seerr.serve("GET /api/v1/request", requestsPage(listOf(5, 4, 3)))

            assertEquals(CheckResult.Ok, w.checker.check())

            assertEquals(listOf(listOf(5, 4)), notifier.newRequests)
            assertEquals("5", w.store.get(PENDING_KEY))
        }

    @Test
    fun `a failed read leaves the cursor, and a refused session is an auth failure`() =
        runTest {
            val w = wiring()
            w.prefs.setEnabled(NotificationSignal.PendingRequests, true)
            seerr.serve("GET /api/v1/request", requestsPage(listOf(2, 1)))
            w.checker.check()

            seerr.serve("GET /api/v1/request", "", code = 500)
            assertEquals(CheckResult.TransientFailure, w.checker.check())
            assertEquals("2", w.store.get(PENDING_KEY))

            seerr.serve("GET /api/v1/request", "", code = 401)
            seerr.serve("GET /api/v1/auth/me", "", code = 401)
            assertEquals(CheckResult.AuthFailure, w.checker.check())
            assertEquals("2", w.store.get(PENDING_KEY))
        }

    @Test
    fun `turning a signal back on starts its cursor over, so what arrived while it was off is not announced`() =
        runTest {
            val w = wiring()
            w.prefs.setEnabled(NotificationSignal.PendingRequests, true)
            seerr.serve("GET /api/v1/request", requestsPage(listOf(2, 1)))
            w.checker.check()
            assertEquals("2", w.store.get(PENDING_KEY))

            w.prefs.setEnabled(NotificationSignal.PendingRequests, false)
            seerr.serve("GET /api/v1/request", requestsPage(listOf(4, 3, 2, 1)))
            w.prefs.setEnabled(NotificationSignal.PendingRequests, true)
            assertNull(w.store.get(PENDING_KEY))

            w.checker.check()
            assertTrue(notifier.newRequests.isEmpty())
            assertEquals("4", w.store.get(PENDING_KEY))
        }

    @Test
    fun `a server change re-seeds instead of announcing the old server's backlog`() =
        runTest {
            val w = wiring()
            w.prefs.setEnabled(NotificationSignal.PendingRequests, true)
            seerr.serve("GET /api/v1/request", requestsPage(listOf(2, 1)))
            w.checker.check()

            w.prefs.forgetServer()
            assertNull(w.store.get(PENDING_KEY))
            seerr.serve("GET /api/v1/request", requestsPage(listOf(9, 8, 7)))

            assertEquals(CheckResult.Ok, w.checker.check())
            assertTrue(notifier.newRequests.isEmpty())
            assertEquals("9", w.store.get(PENDING_KEY))
        }

    @Test
    fun `turning on a signal that was already on leaves the others' cursors alone`() =
        runTest {
            val w = wiring()
            w.prefs.setEnabled(NotificationSignal.PendingRequests, true)
            seerr.serve("GET /api/v1/request", requestsPage(listOf(2, 1)))
            w.checker.check()

            w.prefs.enableIfNoneOn(listOf(NotificationSignal.OpenIssues))

            assertEquals("2", w.store.get(PENDING_KEY))
        }
}
