package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.v1.forwardedNotification
import com.google.protobuf.timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationForwardingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var now = START

    private fun TestScope.forwarding() =
        NotificationForwarding(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") }) { now }

    @Test
    fun `off until Binge turns it on, and turning it on counts as a poll`() =
        runTest {
            val forwarding = forwarding()
            assertFalse(forwarding.enabled.first())
            assertFalse(forwarding.isActive())

            forwarding.setEnabled(true)

            assertTrue(forwarding.enabled.first())
            assertTrue(forwarding.isActive())
        }

    @Test
    fun `lapses when no host polls for a day, and a poll brings it back`() =
        runTest {
            val forwarding = forwarding()
            forwarding.setEnabled(true)

            now += FORWARDING_LAPSE_MILLIS + 1
            assertFalse(forwarding.isActive())
            assertTrue(forwarding.enabled.first())

            forwarding.pending()
            assertTrue(forwarding.isActive())
        }

    @Test
    fun `the queue lists oldest first, acknowledging removes, and an unknown id is ignored`() =
        runTest {
            val forwarding = forwarding()
            forwarding.setEnabled(true)
            forwarding.enqueue(notice("b", atMillis = START + 2_000))
            forwarding.enqueue(notice("a", atMillis = START + 1_000))

            assertEquals(listOf("a", "b"), forwarding.pending().map { it.id })

            forwarding.acknowledge(listOf("a", "never-issued"))
            assertEquals(listOf("b"), forwarding.pending().map { it.id })
        }

    @Test
    fun `turning it off drops the queue, and nothing is listed while off`() =
        runTest {
            val forwarding = forwarding()
            forwarding.setEnabled(true)
            forwarding.enqueue(notice("a", atMillis = START))

            forwarding.setEnabled(false)

            assertFalse(forwarding.isActive())
            assertEquals(emptyList<String>(), forwarding.pending().map { it.id })
            forwarding.setEnabled(true)
            assertEquals(emptyList<String>(), forwarding.pending().map { it.id })
        }

    @Test
    fun `a notice older than a week, or beyond the cap, is dropped unread`() =
        runTest {
            val forwarding = forwarding()
            forwarding.setEnabled(true)
            forwarding.enqueue(notice("stale", atMillis = START))
            now += FORWARDED_MAX_AGE_MILLIS + 1_000
            repeat(FORWARDED_MAX_QUEUED + 1) { forwarding.enqueue(notice("n$it", atMillis = now + it * 1_000L)) }

            val pending = forwarding.pending().map { it.id }

            assertEquals(FORWARDED_MAX_QUEUED, pending.size)
            assertEquals("n1", pending.first())
            assertEquals("n$FORWARDED_MAX_QUEUED", pending.last())
        }

    private fun notice(
        id: String,
        atMillis: Long,
    ) = forwardedNotification {
        this.id = id
        title = id
        createdAt = timestamp { seconds = atMillis / 1_000 }
    }

    private companion object {
        const val START = 1_800_000_000_000L
    }
}
