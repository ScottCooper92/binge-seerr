package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.binge.companion.contracts.v1.ForwardedNotification
import com.binge.companion.contracts.v1.forwardedNotification
import com.google.protobuf.timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

/**
 * What the queue keeps on disk is a promise to installs that already hold one: the preference names, and each notice as the
 * Base64 of its protobuf bytes in a string set. The queue's own behaviour is tested where it lives, in the shared library.
 */
class NotificationForwardingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val enabledKey = booleanPreferencesKey("forwarding_enabled")
    private val lastPollKey = longPreferencesKey("forwarding_last_poll_millis")
    private val queueKey = stringSetPreferencesKey("forwarding_queue")

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") }

    @Test
    fun `a queue the released version wrote is read oldest first, and an unreadable entry is dropped`() =
        runTest {
            val store = store()
            store.edit {
                it[enabledKey] = true
                it[lastPollKey] = NOW
                it[queueKey] =
                    setOf(
                        stored(notice("b", NOW + 2_000)),
                        stored(notice("a", NOW + 1_000)),
                        "not base64!",
                        Base64.getEncoder().encodeToString(byteArrayOf(0x0a)),
                    )
            }

            val pending = seerrForwarding(store) { NOW }.pending()

            assertEquals(listOf("a", "b"), pending.map { it.id })
        }

    @Test
    fun `what it writes is the Base64 of each notice's protobuf under the shipped names`() =
        runTest {
            val store = store()
            val forwarding = seerrForwarding(store) { NOW }
            forwarding.setEnabled(true)

            forwarding.enqueue(notice("a", NOW))

            val prefs: Preferences = store.data.first()
            assertEquals(true, prefs[enabledKey])
            assertEquals(NOW, prefs[lastPollKey])
            assertEquals(setOf(stored(notice("a", NOW))), prefs[queueKey])
        }

    @Test
    fun `the shipped limits are a day of lapse, a week of age and 200 queued`() =
        runTest {
            val store = store()
            var now = NOW
            val forwarding = seerrForwarding(store) { now }
            forwarding.setEnabled(true)
            forwarding.enqueue(notice("stale", NOW))

            now += WEEK + 1_000
            repeat(201) { forwarding.enqueue(notice("n$it", now + it * 1_000L)) }
            val pending = forwarding.pending().map { it.id }

            assertEquals(200, pending.size)
            assertEquals("n1", pending.first())
            now += DAY + 1
            assertTrue(!forwarding.isActive())
        }

    private fun notice(
        id: String,
        atMillis: Long,
    ): ForwardedNotification =
        forwardedNotification {
            this.id = id
            title = id
            createdAt = timestamp { seconds = atMillis / 1_000 }
        }

    private fun stored(notice: ForwardedNotification): String = Base64.getEncoder().encodeToString(notice.toByteArray())

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 24 * 60 * 60 * 1000L
        const val WEEK = 7 * DAY
    }
}
