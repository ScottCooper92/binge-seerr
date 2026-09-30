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
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsConsent
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
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
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ViewModel() {
        private val fetchTrigger = MutableStateFlow(0)

        /** Re-read on every arrival and after the system's notification page: what it allows is not observable. */
        private val blockedTrigger = MutableStateFlow(0)

        /**
         * The trigger, after the signed-in user has been re-read. Refreshed here once rather than in each
         * loader: they run concurrently, so each refreshing would race the others' reads of the cache.
         * `shareIn` with `replay = 1` is what makes that true: a plain cold flow would still be collected
         * once per downstream `flatMapLatest`, refreshing three times per arrival instead of one.
         */
        private val viewerRefreshed: Flow<Int> =
            fetchTrigger
                .mapLatest { trigger -> trigger.also { loader.refreshViewer() } }
                .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

        private val summary: Flow<ConnectionSummary?> =
            viewerRefreshed.flatMapLatest { flow { emit(runCatching { loader.connection() }.getOrNull()) } }.onStart { emit(null) }

        private val server: Flow<ServerSummary?> =
            fetchTrigger.flatMapLatest { flow { emit(runCatching { loader.server() }.getOrNull()) } }.onStart { emit(null) }

        private val config: Flow<ServerConfig?> =
            viewerRefreshed.flatMapLatest { flow { emit(loader.config()) } }.onStart { emit(null) }

        private val offered: Flow<List<NotificationSignal>?> =
            viewerRefreshed.flatMapLatest { flow<List<NotificationSignal>?> { emit(loader.notificationSignals()) } }.onStart { emit(null) }

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
            analytics.event(
                AnalyticsEvents.NOTIFICATION_SIGNAL_CHANGED,
                mapOf(AnalyticsEvents.PARAM_SIGNAL to signal.name, AnalyticsEvents.PARAM_ENABLED to value),
            )
            viewModelScope.launch(dispatcher) { prefs.setEnabled(signal, value) }
        }

        fun setShakeToReport(enabled: Boolean) {
            analytics.event(AnalyticsEvents.SHAKE_TO_REPORT_CHANGED, mapOf(AnalyticsEvents.PARAM_ENABLED to enabled))
            viewModelScope.launch(dispatcher) { feedbackPrefs.setShakeToReport(enabled) }
        }

        fun setShareUsageData(enabled: Boolean) {
            viewModelScope.launch(dispatcher) { telemetryPrefs.setAnalyticsGranted(enabled) }
        }

        fun setSendCrashReports(enabled: Boolean) {
            viewModelScope.launch(dispatcher) { telemetryPrefs.setCrashReportingEnabled(enabled) }
        }

        fun disconnect() {
            analytics.event(AnalyticsEvents.SERVER_DISCONNECTED)
            crashBreadcrumbs.log("disconnecting from server")
            viewModelScope.launch(dispatcher) { connection.disconnect() }
        }
    }
