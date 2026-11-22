package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.cooper.homelab.core.notifications.POLL_INTERVAL_CHOICES
import com.cooper.homelab.core.notifications.PollPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Set by [PollReactor] when the server rejects the saved credentials, cleared by [forgetServer]. */
private val PAUSED_FOR_AUTH_FAILURE = booleanPreferencesKey("paused_for_auth_failure")

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
}

private val LAST_RUN = longPreferencesKey("last_run_millis")

/** The poll's interval when none is stored, in minutes: WorkManager's 15 is the floor. */
const val DEFAULT_POLL_INTERVAL_MINUTES: Int = 15

/**
 * The user's own requests that still have an outcome to announce. Shared by the three own-request
 * signals, because it is not deduplication: it is how deep the next walk of the list has to go
 * before every request it is still watching has been accounted for (#181).
 */
private val WATCHED_REQUEST_IDS = stringSetPreferencesKey("own_requests_watching")

/**
 * The poll's own DataStore: a toggle per signal, and each signal's deduplication state. A null
 * cursor or set is unseeded; the next check seeds it without notifying, so turning a signal on
 * does not announce the backlog. Everything defaults off.
 */
class NotificationPrefs(
    private val dataStore: DataStore<Preferences>,
    /** Starts a signal's cursor over wherever the cursors are kept; [CursorReset.None] while they are the preferences below. */
    private val cursorReset: CursorReset = CursorReset.None,
) {
    /** The interval is the shared library's. It stores under `poll_interval_minutes`, the key this app always has. */
    private val pollPrefs = PollPrefs(dataStore, DEFAULT_POLL_INTERVAL_MINUTES)

    fun enabled(signal: NotificationSignal): Flow<Boolean> = dataStore.data.map { it[signal.toggleKey()] ?: false }.distinctUntilChanged()

    /** On while any signal is; what the poll's schedule follows. */
    val anyEnabled: Flow<Boolean> =
        combine(NotificationSignal.entries.map(::enabled)) { signals -> signals.any { it } }.distinctUntilChanged()

    suspend fun isEnabled(signal: NotificationSignal): Boolean = enabled(signal).first()

    /**
     * Whether a poll's rejected credentials have paused the schedule. Tracked independently of the
     * credentials themselves so re-entering the same, still-rejected key does not look like a no-op
     * to [NotificationPlanner]: re-arming the poll needs its own signal, not structural equality.
     */
    val pausedForAuthFailure: Flow<Boolean> = dataStore.data.map { it[PAUSED_FOR_AUTH_FAILURE] ?: false }.distinctUntilChanged()

    suspend fun setPausedForAuthFailure(paused: Boolean) {
        dataStore.edit { it[PAUSED_FOR_AUTH_FAILURE] = paused }
    }

    /**
     * Enabling a signal drops its deduplication state, so what arrived while it was off is not announced. The shared cursor goes
     * first and the toggle after, so a poll that runs in between still sees the signal off and skips it, rather than finding
     * the old cursor and announcing the gap.
     */
    suspend fun setEnabled(
        signal: NotificationSignal,
        value: Boolean,
    ) {
        if (value) cursorReset.reset(signal)
        dataStore.edit { prefs ->
            prefs[signal.toggleKey()] = value
            if (value) prefs.clearDedup(signal)
        }
    }

    /** Turns [signals] on only when no signal is on at all, leaving a choice the user has made alone. */
    suspend fun enableIfNoneOn(signals: List<NotificationSignal>) {
        // Decided here so the shared cursors can go before the toggles do; the edit re-checks, so a change in between turns nothing
        // on twice. A cursor dropped for a signal that stays off only means it seeds again when it is turned on.
        if (NotificationSignal.entries.any { isEnabled(it) }) return
        signals.forEach { cursorReset.reset(it) }
        dataStore.edit { prefs ->
            if (NotificationSignal.entries.none { prefs[it.toggleKey()] == true }) {
                signals.forEach { signal ->
                    prefs[signal.toggleKey()] = true
                    prefs.clearDedup(signal)
                }
            }
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

    /**
     * The ids the own-requests walk is still watching, or null before it has ever run — which is
     * the seed, and walks the whole list once to find what was already outstanding.
     */
    suspend fun watchedRequestIds(): Set<Int>? =
        dataStore.data.first()[WATCHED_REQUEST_IDS]?.mapNotNullTo(mutableSetOf()) { it.toIntOrNull() }

    suspend fun setWatchedRequestIds(ids: Set<Int>) {
        dataStore.edit { it[WATCHED_REQUEST_IDS] = ids.mapTo(mutableSetOf()) { id -> id.toString() } }
    }

    /** How often the poll is asked to run, always one of [POLL_INTERVAL_CHOICES]; Android may run it less often than this. */
    val pollIntervalMinutes: Flow<Int> = pollPrefs.intervalMinutes

    suspend fun setPollIntervalMinutes(minutes: Int) {
        require(minutes in POLL_INTERVAL_CHOICES) { "No poll interval of $minutes minutes" }
        pollPrefs.setIntervalMinutes(minutes)
    }

    /** When the poll last finished, for the settings row; null before its first run. */
    val lastRunMillis: Flow<Long?> = dataStore.data.map { it[LAST_RUN] }.distinctUntilChanged()

    suspend fun setLastRun(millis: Long) {
        dataStore.edit { it[LAST_RUN] = millis }
    }

    /**
     * Drops every signal's deduplication state; the next check re-seeds. For a server change, whose
     * ids mean nothing here. Also clears [pausedForAuthFailure]: this runs on every successful
     * connect and on disconnect (`SeerrConnection.onServerChanged`), so it re-arms a paused poll
     * even when the credentials that fixed it are byte-for-byte the ones that were rejected.
     */
    suspend fun forgetServer() {
        dataStore.edit { prefs ->
            NotificationSignal.entries.forEach { prefs.clearDedup(it) }
            prefs.remove(WATCHED_REQUEST_IDS)
            prefs.remove(PAUSED_FOR_AUTH_FAILURE)
        }
        cursorReset.resetAll()
    }
}

private fun MutablePreferences.clearDedup(signal: NotificationSignal) {
    remove(signal.cursorKey())
    remove(signal.notifiedKey())
}

private fun NotificationSignal.toggleKey() = booleanPreferencesKey("signal_${key}_enabled")

private fun NotificationSignal.cursorKey() = intPreferencesKey("signal_${key}_cursor")

private fun NotificationSignal.notifiedKey() = stringSetPreferencesKey("signal_${key}_notified")
