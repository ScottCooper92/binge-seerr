package io.github.scottcooper92.binge.seerr.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What the poll can tell the user about, each behind its own toggle. The first two are feeds a
 * moderator watches, deduplicated by a last-seen id; the rest are the user's own requests
 * changing state, deduplicated by the set already told about.
 */
enum class NotificationSignal(
    val key: String,
) {
    PendingRequests("pending_requests"),
    OpenIssues("open_issues"),
    RequestAvailable("request_available"),
    RequestApproved("request_approved"),
    RequestDeclined("request_declined"),
    ;

    val isFeed: Boolean get() = this == PendingRequests || this == OpenIssues
}

private val LAST_RUN = longPreferencesKey("last_run_millis")

/**
 * The poll's own DataStore: a toggle per signal, and each signal's deduplication state. A null
 * cursor or set is unseeded; the next check seeds it without notifying, so turning a signal on
 * does not announce the backlog. Everything defaults off.
 */
class NotificationPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    fun enabled(signal: NotificationSignal): Flow<Boolean> = dataStore.data.map { it[signal.toggleKey()] ?: false }.distinctUntilChanged()

    /** On while any signal is; what the poll's schedule follows. */
    val anyEnabled: Flow<Boolean> =
        combine(NotificationSignal.entries.map(::enabled)) { signals -> signals.any { it } }.distinctUntilChanged()

    suspend fun isEnabled(signal: NotificationSignal): Boolean = enabled(signal).first()

    /** Enabling a signal drops its deduplication state, so what arrived while it was off is not announced. */
    suspend fun setEnabled(
        signal: NotificationSignal,
        value: Boolean,
    ) {
        dataStore.edit { prefs ->
            prefs[signal.toggleKey()] = value
            if (value) prefs.clearDedup(signal)
        }
    }

    suspend fun cursor(signal: NotificationSignal): Int? = dataStore.data.first()[signal.cursorKey()]

    suspend fun setCursor(
        signal: NotificationSignal,
        id: Int,
    ) {
        dataStore.edit { it[signal.cursorKey()] = id }
    }

    suspend fun notifiedIds(signal: NotificationSignal): Set<Int>? =
        dataStore.data.first()[signal.notifiedKey()]?.mapNotNullTo(mutableSetOf()) { it.toIntOrNull() }

    suspend fun setNotifiedIds(
        signal: NotificationSignal,
        ids: Set<Int>,
    ) {
        dataStore.edit { it[signal.notifiedKey()] = ids.mapTo(mutableSetOf()) { id -> id.toString() } }
    }

    /** When the poll last finished, for the settings row; null before its first run. */
    val lastRunMillis: Flow<Long?> = dataStore.data.map { it[LAST_RUN] }.distinctUntilChanged()

    suspend fun setLastRun(millis: Long) {
        dataStore.edit { it[LAST_RUN] = millis }
    }

    /** Drops every signal's deduplication state; the next check re-seeds. For a server change, whose ids mean nothing here. */
    suspend fun forgetServer() {
        dataStore.edit { prefs -> NotificationSignal.entries.forEach { prefs.clearDedup(it) } }
    }

    private fun MutablePreferences.clearDedup(signal: NotificationSignal) {
        remove(signal.cursorKey())
        remove(signal.notifiedKey())
    }

    private fun NotificationSignal.toggleKey() = booleanPreferencesKey("signal_${key}_enabled")

    private fun NotificationSignal.cursorKey() = intPreferencesKey("signal_${key}_cursor")

    private fun NotificationSignal.notifiedKey() = stringSetPreferencesKey("signal_${key}_notified")
}
