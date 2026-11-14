package com.cooper.binge.companion.seerr.ui.settings

import com.cooper.binge.companion.seerr.notifications.BackgroundAccess
import com.cooper.binge.companion.seerr.notifications.NotificationForwarding
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.notifications.NotificationScheduler
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.cooper.binge.companion.seerr.notifications.SeerrNotifier
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * This app's own poll, as the This app page shows it: the signals this viewer may turn on, which are on, whether the system
 * would show anything, whether Binge shows them instead, how often it runs and whether Android holds it back, and when the poll
 * ran and runs next. The signals offered are read
 * from the server on each arrival, because they follow the viewer's permissions; everything else is on the device.
 *
 * @param now moves on each minute while the page shows, so [forwarding]'s recent-poll rule and the schedule are re-read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PollNotificationsSource(
    private val loader: SettingsLoader,
    private val prefs: NotificationPrefs,
    scheduler: NotificationScheduler,
    private val notifier: SeerrNotifier,
    private val forwarding: NotificationForwarding,
    private val background: BackgroundAccess,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    now: Flow<Long>,
) {
    private val fetchTrigger = MutableStateFlow(0)

    /** Re-read on every arrival and after the system's notification page: what it allows is not observable. */
    private val blockedTrigger = MutableStateFlow(0)

    private val offered: Flow<List<NotificationSignal>> =
        fetchTrigger.flatMapLatest {
            flow {
                loader.refreshViewer()
                emit(loader.notificationSignals())
            }
        }

    private val enabled: Flow<Set<NotificationSignal>> =
        combine(
            NotificationSignal.entries.map { signal ->
                prefs.enabled(signal).map { on -> signal.takeIf { on } }
            },
        ) { it.filterNotNull().toSet() }

    /** Null until the signals on offer have been read. */
    val settings: Flow<NotificationSettings?> =
        combine(
            offered,
            combine(enabled, forwarding.enabled, prefs.pollIntervalMinutes, now) { enabled, switchedOn, interval, _ ->
                Triple(enabled, switchedOn, interval)
            },
            blockedTrigger,
            prefs.lastRunMillis,
            scheduler.nextRunMillis(),
        ) { offered, (enabled, switchedOn, interval), _, last, next ->
            // Routing asks isActive(), which also needs a recent poll; the row follows the same rule.
            NotificationSettings(
                offered = offered,
                enabled = enabled.filterTo(mutableSetOf()) { signal -> signal in offered },
                blocked = !notifier.canPost(),
                lastRunMillis = last,
                nextRunMillis = next,
                forwardedToBinge = switchedOn && forwarding.isActive(),
                intervalMinutes = interval,
                unrestricted = background.isUnrestricted(),
            )
        }

    /** On every arrival at the page, so a permission changed on the server shows without leaving the app. */
    fun refresh() {
        fetchTrigger.value++
        blockedTrigger.value++
    }

    /** After the system's notification page or the permission prompt: what they decided is only readable, not observable. */
    fun recheckAccess() {
        blockedTrigger.value++
    }

    fun setInterval(minutes: Int) {
        scope.launch(dispatcher) { prefs.setPollIntervalMinutes(minutes) }
    }

    fun setSignal(
        signal: NotificationSignal,
        value: Boolean,
    ) {
        analytics.event(
            AnalyticsEvents.NOTIFICATION_SIGNAL_CHANGED,
            mapOf(AnalyticsEvents.PARAM_SIGNAL to signal.name, AnalyticsEvents.PARAM_ENABLED to value),
        )
        scope.launch(dispatcher) { prefs.setEnabled(signal, value) }
    }
}
