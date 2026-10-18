package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.binge.companion.contracts.v1.ForwardedNotification
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Base64

private val FORWARDING_ENABLED = booleanPreferencesKey("forwarding_enabled")
private val FORWARDING_LAST_POLL = longPreferencesKey("forwarding_last_poll_millis")
private val FORWARDING_QUEUE = stringSetPreferencesKey("forwarding_queue")

/** How long without a host polling before the notices go back to this app (binge-companions#249). */
internal const val FORWARDING_LAPSE_MILLIS = 24 * 60 * 60 * 1000L

/** The oldest a queued notice may get before it is dropped unread. */
internal const val FORWARDED_MAX_AGE_MILLIS = 7 * 24 * 60 * 60 * 1000L

/** The most notices kept waiting; the oldest go first beyond it. */
internal const val FORWARDED_MAX_QUEUED = 200

/**
 * Whether Binge shows this app's notices instead of this app, and the queue it reads them from
 * (`CAPABILITY_FORWARD_NOTIFICATIONS`). It is *active* while on and a host has polled within
 * [FORWARDING_LAPSE_MILLIS]; turning it on counts as a poll. A host uninstalled without turning it
 * off stops polling, so the notices come back here rather than queueing for nobody.
 */
class NotificationForwarding(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val enabled: Flow<Boolean> = dataStore.data.map { it[FORWARDING_ENABLED] ?: false }.distinctUntilChanged()

    suspend fun isActive(): Boolean {
        val prefs = dataStore.data.first()
        val lastPoll = prefs[FORWARDING_LAST_POLL] ?: return false
        return prefs[FORWARDING_ENABLED] == true && clock() - lastPoll <= FORWARDING_LAPSE_MILLIS
    }

    /** Off drops the queue: Binge will not read it, and this app shows its own from here on. */
    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[FORWARDING_ENABLED] = value
            if (value) {
                prefs[FORWARDING_LAST_POLL] = clock()
            } else {
                prefs.remove(FORWARDING_LAST_POLL)
                prefs.remove(FORWARDING_QUEUE)
            }
        }
    }

    suspend fun enqueue(notification: ForwardedNotification) {
        dataStore.edit { prefs ->
            prefs.writeQueue(prefs.readQueue().filterNot { it.id == notification.id } + notification)
        }
    }

    /** What waits for the host, oldest first. Reading is the host polling, so it keeps forwarding active. */
    suspend fun pending(): List<ForwardedNotification> {
        var queue = emptyList<ForwardedNotification>()
        dataStore.edit { prefs ->
            if (prefs[FORWARDING_ENABLED] == true) {
                prefs[FORWARDING_LAST_POLL] = clock()
                queue = prefs.readQueue().pruned()
                prefs.writeQueue(queue)
            }
        }
        return queue
    }

    /** An id that is not queued is ignored, so a retried acknowledgement is harmless. */
    suspend fun acknowledge(ids: Collection<String>) {
        val acknowledged = ids.toSet()
        dataStore.edit { prefs -> prefs.writeQueue(prefs.readQueue().filterNot { it.id in acknowledged }) }
    }

    /** Drops every queued notice of [kind], for one that has stopped being true before the host read it. */
    suspend fun dropKind(kind: String) {
        dataStore.edit { prefs -> prefs.writeQueue(prefs.readQueue().filterNot { it.kind == kind }) }
    }

    private fun MutablePreferences.readQueue(): List<ForwardedNotification> =
        this[FORWARDING_QUEUE]
            .orEmpty()
            .mapNotNull { it.decodeNotification() }
            .sortedWith(compareBy({ it.createdAt.seconds }, { it.createdAt.nanos }, { it.id }))

    private fun MutablePreferences.writeQueue(queue: List<ForwardedNotification>) {
        this[FORWARDING_QUEUE] = queue.pruned().mapTo(mutableSetOf()) { Base64.getEncoder().encodeToString(it.toByteArray()) }
    }

    /** Drops what is too old, then the oldest beyond the cap. */
    private fun List<ForwardedNotification>.pruned(): List<ForwardedNotification> {
        val oldest = (clock() - FORWARDED_MAX_AGE_MILLIS) / MILLIS_PER_SECOND
        return filter { it.createdAt.seconds >= oldest }.takeLast(FORWARDED_MAX_QUEUED)
    }
}

private const val MILLIS_PER_SECOND = 1000L

/** A stored entry this build cannot read is dropped rather than failing the whole queue. */
private fun String.decodeNotification(): ForwardedNotification? =
    try {
        ForwardedNotification.parseFrom(Base64.getDecoder().decode(this))
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: InvalidProtocolBufferException) {
        null
    }
