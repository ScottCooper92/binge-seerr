package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.binge.companion.contracts.notifications.v1.HandshakeRequest
import com.binge.companion.contracts.notifications.v1.ListNotificationsRequest
import com.binge.companion.contracts.notifications.v1.NotificationsServiceGrpcKt
import com.binge.companion.contracts.notifications.v1.setNotificationForwardingRequest
import com.cooper.binge.companion.seerr.notifications.NotificationForwarding
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SeerrNotificationsServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: Server
    private lateinit var channel: ManagedChannel

    @After
    fun tearDown() {
        if (::channel.isInitialized) channel.shutdownNow()
        if (::server.isInitialized) server.shutdownNow()
    }

    private fun TestScope.stub(): NotificationsServiceGrpcKt.NotificationsServiceCoroutineStub {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("n.preferences_pb") }
        val service = SeerrNotificationsService(SeerrNotificationRpcs(NotificationForwarding(store), NotificationPrefs(store)))
        val name = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(name).directExecutor().addService(service).build().start()
        channel = InProcessChannelBuilder.forName(name).directExecutor().build()
        return NotificationsServiceGrpcKt.NotificationsServiceCoroutineStub(channel)
    }

    @Test
    fun `the handshake answers with no session and declares no capabilities yet`() =
        runTest {
            val response = stub().handshake(HandshakeRequest.getDefaultInstance())

            assertTrue(response.capabilitiesList.isEmpty())
        }

    @Test
    fun `forwarding is switched on and the empty queue listed through the service`() =
        runTest {
            val stub = stub()

            stub.setNotificationForwarding(setNotificationForwardingRequest { enabled = true })

            assertEquals(0, stub.listNotifications(ListNotificationsRequest.getDefaultInstance()).notificationsCount)
        }
}
