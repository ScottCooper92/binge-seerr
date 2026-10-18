package com.cooper.binge.companion.seerr.service

import com.binge.companion.contracts.notifications.v1.AcknowledgeNotificationsRequest
import com.binge.companion.contracts.notifications.v1.AcknowledgeNotificationsResponse
import com.binge.companion.contracts.notifications.v1.HandshakeRequest
import com.binge.companion.contracts.notifications.v1.HandshakeResponse
import com.binge.companion.contracts.notifications.v1.ListNotificationsRequest
import com.binge.companion.contracts.notifications.v1.ListNotificationsResponse
import com.binge.companion.contracts.notifications.v1.NotificationsServiceGrpcKt
import com.binge.companion.contracts.notifications.v1.SetNotificationForwardingRequest
import com.binge.companion.contracts.notifications.v1.SetNotificationForwardingResponse
import com.cooper.binge.companion.seerr.seerr.statusCatching

/**
 * NOTIFICATIONS v1: the forwarding queue, served apart from REQUEST so a host that never asks for
 * requests can still collect notifications. Needs no Seerr session, so nothing here answers
 * UNAUTHENTICATED, the handshake included. It declares no capabilities yet.
 *
 * Failures are not reported to analytics, which only takes a server-version signal and has no server here: a queue
 * that cannot be read is the app's own storage failing, not a Seerr release to learn about.
 */
class SeerrNotificationsService(
    private val rpcs: SeerrNotificationRpcs,
) : NotificationsServiceGrpcKt.NotificationsServiceCoroutineImplBase() {
    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse = HandshakeResponse.getDefaultInstance()

    override suspend fun setNotificationForwarding(request: SetNotificationForwardingRequest): SetNotificationForwardingResponse =
        statusCatching { rpcs.setNotificationForwarding(request) }

    override suspend fun listNotifications(request: ListNotificationsRequest): ListNotificationsResponse =
        statusCatching { rpcs.listNotifications(request) }

    override suspend fun acknowledgeNotifications(request: AcknowledgeNotificationsRequest): AcknowledgeNotificationsResponse =
        statusCatching { rpcs.acknowledgeNotifications(request) }
}
