package com.cooper.binge.companion.seerr.notifications

import com.binge.companion.contracts.v1.ForwardedNotification
import com.cooper.homelab.core.notifications.ForwardedCodec
import com.cooper.homelab.core.notifications.ForwardingKeys
import com.cooper.homelab.core.notifications.NotificationForwarding
import com.google.protobuf.InvalidProtocolBufferException

/** The preference names the forwarding queue has always been stored under. They are an on-disk format: never change them. */
internal val SeerrForwardingKeys =
    ForwardingKeys(
        enabled = "forwarding_enabled",
        lastPollMillis = "forwarding_last_poll_millis",
        queue = "forwarding_queue",
    )

/** The queue of notices Binge reads, in this app's contract type. */
typealias SeerrForwarding = NotificationForwarding<ForwardedNotification>

/** A [ForwardedNotification] as the bytes the queue keeps: its own protobuf encoding, as it has always been stored. */
internal object ForwardedNotificationCodec : ForwardedCodec<ForwardedNotification> {
    override fun encode(notice: ForwardedNotification): ByteArray = notice.toByteArray()

    override fun decode(bytes: ByteArray): ForwardedNotification? =
        try {
            ForwardedNotification.parseFrom(bytes)
        } catch (_: InvalidProtocolBufferException) {
            null
        }

    override fun id(notice: ForwardedNotification): String = notice.id

    override fun kind(notice: ForwardedNotification): String = notice.kind

    override fun createdAtMillis(notice: ForwardedNotification): Long =
        notice.createdAt.seconds * MILLIS_PER_SECOND + notice.createdAt.nanos / NANOS_PER_MILLI
}

private const val MILLIS_PER_SECOND = 1000L
private const val NANOS_PER_MILLI = 1_000_000
