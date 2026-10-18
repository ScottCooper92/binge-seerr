package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.notifications.v1.ListNotificationsRequest
import com.binge.companion.contracts.notifications.v1.acknowledgeNotificationsRequest
import com.binge.companion.contracts.notifications.v1.listNotificationsRequest
import com.binge.companion.contracts.notifications.v1.setNotificationForwardingRequest
import com.binge.companion.contracts.v1.forwardedNotification
import com.cooper.binge.companion.seerr.notifications.NotificationForwarding
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.google.protobuf.timestamp
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SeerrNotificationRpcsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var prefs: NotificationPrefs
    private lateinit var forwarding: NotificationForwarding

    private fun TestScope.rpcs(): SeerrNotificationRpcs {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("n.preferences_pb") }
        prefs = NotificationPrefs(store)
        forwarding = NotificationForwarding(store) { NOW }
        return SeerrNotificationRpcs(forwarding, prefs)
    }

    @Test
    fun `turning forwarding on with nothing chosen turns on the user's own requests, so Binge has something to show`() =
        runTest {
            val rpcs = rpcs()

            rpcs.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })

            assertTrue(forwarding.isActive())
            assertTrue(prefs.isEnabled(NotificationSignal.RequestAvailable))
            assertTrue(prefs.isEnabled(NotificationSignal.RequestApproved))
            assertTrue(prefs.isEnabled(NotificationSignal.RequestDeclined))
            assertFalse(prefs.isEnabled(NotificationSignal.PendingRequests))
        }

    @Test
    fun `a choice the user already made in this app is left alone`() =
        runTest {
            val rpcs = rpcs()
            prefs.setEnabled(NotificationSignal.OpenIssues, true)

            rpcs.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })

            assertTrue(prefs.isEnabled(NotificationSignal.OpenIssues))
            assertFalse(prefs.isEnabled(NotificationSignal.RequestAvailable))
        }

    @Test
    fun `turning it off keeps this app's kinds, and it goes back to showing them`() =
        runTest {
            val rpcs = rpcs()
            rpcs.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })

            rpcs.setNotificationForwarding(setNotificationForwardingRequest { enabled = false })

            assertFalse(forwarding.isActive())
            assertTrue(prefs.isEnabled(NotificationSignal.RequestAvailable))
        }

    @Test
    fun `lists in pages under the token it issued, then acknowledges`() =
        runTest {
            val rpcs = rpcs()
            rpcs.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })
            repeat(3) { forwarding.enqueue(notice("n$it", it)) }

            val first = rpcs.listNotifications(listNotificationsRequest { pageSize = 2 })
            val second =
                rpcs.listNotifications(
                    listNotificationsRequest {
                        pageSize = 2
                        pageToken = first.nextPageToken
                    },
                )

            assertEquals(listOf("n0", "n1"), first.notificationsList.map { it.id })
            assertEquals(listOf("n2"), second.notificationsList.map { it.id })
            assertEquals("", second.nextPageToken)

            rpcs.acknowledgeNotifications(acknowledgeNotificationsRequest { ids += listOf("n0", "n1", "n2") })
            assertEquals(0, rpcs.listNotifications(ListNotificationsRequest.getDefaultInstance()).notificationsCount)
        }

    @Test
    fun `acknowledging page one before page two skips nothing that was not acknowledged`() =
        runTest {
            val rpcs = rpcs()
            rpcs.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })
            repeat(5) { forwarding.enqueue(notice("n$it", it)) }

            val first = rpcs.listNotifications(listNotificationsRequest { pageSize = 2 })
            rpcs.acknowledgeNotifications(acknowledgeNotificationsRequest { ids += first.notificationsList.map { it.id } })
            val second =
                rpcs.listNotifications(
                    listNotificationsRequest {
                        pageSize = 2
                        pageToken = first.nextPageToken
                    },
                )

            assertEquals(listOf("n2", "n3"), second.notificationsList.map { it.id })
            assertTrue(second.nextPageToken.isNotEmpty())
        }

    @Test
    fun `a token under another page size, or one it never issued, is INVALID_ARGUMENT`() =
        runTest {
            val rpcs = rpcs()
            rpcs.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })
            repeat(3) { forwarding.enqueue(notice("n$it", it)) }
            val token = rpcs.listNotifications(listNotificationsRequest { pageSize = 2 }).nextPageToken

            assertInvalid {
                rpcs.listNotifications(
                    listNotificationsRequest {
                        pageSize = 1
                        pageToken = token
                    },
                )
            }
            assertInvalid { rpcs.listNotifications(listNotificationsRequest { pageToken = "garbage" }) }
        }

    @Test
    fun `more than 100 ids in one acknowledgement is INVALID_ARGUMENT`() =
        runTest {
            val rpcs = rpcs()

            assertInvalid { rpcs.acknowledgeNotifications(acknowledgeNotificationsRequest { ids += (0..100).map { "n$it" } }) }
        }

    private suspend fun assertInvalid(block: suspend () -> Unit) {
        try {
            block()
            fail("expected INVALID_ARGUMENT")
        } catch (e: StatusException) {
            assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
        }
    }

    private fun notice(
        id: String,
        order: Int,
    ) = forwardedNotification {
        this.id = id
        title = id
        createdAt = timestamp { seconds = NOW / 1_000 + order }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
