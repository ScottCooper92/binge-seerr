package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.v1.MediaType
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ForwardingNotifierTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val local = FakeNotifier()
    private var now = NOW
    private lateinit var forwarding: NotificationForwarding

    private fun TestScope.notifier(): ForwardingNotifier {
        forwarding =
            NotificationForwarding(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") },
            ) { NOW }
        return ForwardingNotifier(local, forwarding, FakeText) { now++ }
    }

    @Test
    fun `posts itself while Binge is not forwarding`() =
        runTest {
            val notifier = notifier()

            notifier.notifyRequestsAvailable(listOf(request(1)))

            assertEquals(listOf(listOf(1)), local.available)
            assertTrue(forwarding.pending().isEmpty())
        }

    @Test
    fun `queues instead of posting while Binge forwards, never both`() =
        runTest {
            val notifier = notifier()
            forwarding.setEnabled(true)

            notifier.notifyRequestsApproved(listOf(request(7)))
            notifier.notifyNewRequests(listOf(request(8)))
            notifier.notifyRequestsDeclined(listOf(request(9)))

            assertTrue(local.approved.isEmpty() && local.newRequests.isEmpty() && local.declined.isEmpty())
            assertEquals(
                listOf("request_approved", "pending_requests", "request_declined"),
                forwarding.pending().map { it.kind },
            )
        }

    @Test
    fun `a batch of one names its title, with the words this app would have posted`() =
        runTest {
            val notifier = notifier()
            forwarding.setEnabled(true)

            notifier.notifyRequestsAvailable(listOf(request(42, tmdb = 426063, type = RequestMediaType.Tv)))

            val notice = forwarding.pending().single()
            assertEquals("request_available:42:$NOW", notice.id)
            assertEquals("request_available x1", notice.title)
            assertEquals("Title 42", notice.text)
            assertEquals(MediaType.MEDIA_TYPE_TV, notice.media.mediaType)
            assertEquals(426063, notice.media.tmdbId)
            assertEquals(NOW / 1_000, notice.createdAt.seconds)
        }

    @Test
    fun `a larger batch names no title, so a tap opens the companion`() =
        runTest {
            val notifier = notifier()
            forwarding.setEnabled(true)

            notifier.notifyRequestsAvailable(listOf(request(3), request(5)))

            val notice = forwarding.pending().single()
            assertEquals("request_available:5:$NOW", notice.id)
            assertEquals("Title 3 and 1 more", notice.text)
            assertFalse(notice.hasMedia())
        }

    @Test
    fun `the connection notice is forwarded too, about no title`() =
        runTest {
            val notifier = notifier()
            forwarding.setEnabled(true)

            notifier.notifyConnectionProblem()

            assertEquals(0, local.connectionProblems)
            val notice = forwarding.pending().single()
            assertEquals(FORWARDED_KIND_CONNECTION, notice.kind)
            assertEquals("Sign in again", notice.title)
            assertFalse(notice.hasMedia())
        }

    @Test
    fun `can post while forwarding even when this app may not`() =
        runTest {
            val notifier = notifier()
            local.canPost = false
            assertFalse(notifier.canPost())

            forwarding.setEnabled(true)

            assertTrue(notifier.canPost())
        }

    private fun request(
        id: Int,
        tmdb: Int = 1000 + id,
        type: RequestMediaType = RequestMediaType.Movie,
    ) = RequestItem(
        id = id,
        tmdbId = tmdb,
        mediaType = type,
        title = "Title $id",
        posterUrl = null,
        year = null,
        requestedBy = null,
        requestedById = null,
        requestedAtMillis = null,
        status = null,
        mediaStatus = null,
        download = null,
        seasonNumbers = emptyList(),
        is4k = false,
    )

    private object FakeText : NotificationText {
        override fun title(
            signal: NotificationSignal,
            count: Int,
        ) = "${signal.key} x$count"

        override fun body(
            titles: List<String>,
            count: Int,
        ) = summaryLine(titles, count) { first, others -> "$first and $others more" }

        override fun connectionTitle() = "Sign in again"

        override fun connectionBody() = "The server rejected the saved sign-in"
    }

    private companion object {
        const val NOW = 1_800_000_000_123L
    }
}
