package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.feedback.BugReportLinks
import io.github.scottcooper92.binge.seerr.feedback.FeedbackPrefs
import io.github.scottcooper92.binge.seerr.notifications.NotificationPrefs
import io.github.scottcooper92.binge.seerr.notifications.NotificationScheduler
import io.github.scottcooper92.binge.seerr.notifications.NotificationSignal
import io.github.scottcooper92.binge.seerr.notifications.SeerrNotifier
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsConsent
import io.github.scottcooper92.binge.seerr.telemetry.TelemetryPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings: the connection, the server, the poll's toggles, the admin's read-only view of the
 * server's configuration, and this app's own bug reporting. Every fetch re-runs on the screen becoming visible, so returning from
 * Edit connection shows the new server; each flow's null seed holds the previous value while a
 * refetch is in flight rather than blanking its rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val loader: SettingsLoader,
        private val prefs: NotificationPrefs,
        scheduler: NotificationScheduler,
        private val notifier: SeerrNotifier,
        private val feedbackPrefs: FeedbackPrefs,
        private val telemetryPrefs: TelemetryPrefs,
        bugReportLinks: BugReportLinks,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val fetchTrigger = MutableStateFlow(0)

        /** Re-read on every arrival and after the system's notification page: what it allows is not observable. */
        private val blockedTrigger = MutableStateFlow(0)

        private val summary: Flow<ConnectionSummary?> =
            fetchTrigger.flatMapLatest { flow { emit(runCatching { loader.connection() }.getOrNull()) } }.onStart { emit(null) }

        private val server: Flow<ServerSummary?> =
            fetchTrigger.flatMapLatest { flow { emit(runCatching { loader.server() }.getOrNull()) } }.onStart { emit(null) }

        private val config: Flow<ServerConfig?> =
            fetchTrigger.flatMapLatest { flow { emit(loader.config()) } }.onStart { emit(null) }

        private val offered: Flow<List<NotificationSignal>?> =
            fetchTrigger.flatMapLatest { flow<List<NotificationSignal>?> { emit(loader.notificationSignals()) } }.onStart { emit(null) }

        private val enabled: Flow<Set<NotificationSignal>> =
            combine(
                NotificationSignal.entries.map { signal ->
                    prefs.enabled(signal).map { on -> signal.takeIf { on } }
                },
            ) { it.filterNotNull().toSet() }

        private val notifications: Flow<NotificationSettings?> =
            combine(offered, enabled, blockedTrigger, prefs.lastRunMillis, scheduler.nextRunMillis()) { offered, enabled, _, last, next ->
                offered?.let {
                    NotificationSettings(
                        offered = it,
                        enabled = enabled.filterTo(mutableSetOf()) { signal -> signal in it },
                        blocked = !notifier.canPost(),
                        lastRunMillis = last,
                        nextRunMillis = next,
                    )
                }
            }

        private val app: Flow<AppSettings> =
            combine(
                feedbackPrefs.shakeToReport,
                telemetryPrefs.analyticsConsent,
                telemetryPrefs.crashReportingEnabled,
            ) { shake, consent, crashes ->
                AppSettings(
                    bugReportUrl = bugReportLinks.url(),
                    shakeToReport = shake,
                    shareUsageData = consent == AnalyticsConsent.GRANTED,
                    sendCrashReports = crashes,
                )
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(summary, server, config, notifications, app) { summary, server, config, notifications, app ->
                if (summary == null || server == null) {
                    SettingsUiState.Loading
                } else {
                    SettingsUiState.Ready(summary, server, config, notifications, app)
                }
            }.flowOn(dispatcher)
                .stateIn(viewModelScope, SharingStarted.Lazily, SettingsUiState.Loading)

        fun setScreenVisible(visible: Boolean) {
            if (visible) {
                fetchTrigger.value++
                blockedTrigger.value++
            }
        }

        /** After the system's notification page or the permission prompt: what they decided is only readable, not observable. */
        fun recheckNotificationAccess() {
            blockedTrigger.value++
        }

        fun setSignal(
            signal: NotificationSignal,
            value: Boolean,
        ) {
            viewModelScope.launch(dispatcher) { prefs.setEnabled(signal, value) }
        }

        fun setShakeToReport(enabled: Boolean) {
            viewModelScope.launch(dispatcher) { feedbackPrefs.setShakeToReport(enabled) }
        }

        fun setShareUsageData(enabled: Boolean) {
            viewModelScope.launch(dispatcher) { telemetryPrefs.setAnalyticsGranted(enabled) }
        }

        fun setSendCrashReports(enabled: Boolean) {
            viewModelScope.launch(dispatcher) { telemetryPrefs.setCrashReportingEnabled(enabled) }
        }

        fun disconnect() {
            viewModelScope.launch(dispatcher) { connection.disconnect() }
        }
    }
