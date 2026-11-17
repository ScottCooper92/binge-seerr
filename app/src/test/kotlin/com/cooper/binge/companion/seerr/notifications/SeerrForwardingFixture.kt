package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.homelab.core.bridge.NotificationForwarding
import com.cooper.homelab.core.foundation.Clock
import kotlin.time.Instant

/** The forwarding queue as the app builds it, over [store], reading its time from [now]. */
internal fun seerrForwarding(
    store: DataStore<Preferences>,
    now: () -> Long = System::currentTimeMillis,
): SeerrForwarding =
    NotificationForwarding(store, SeerrForwardingKeys, ForwardedNotificationCodec, Clock { Instant.fromEpochMilliseconds(now()) })
