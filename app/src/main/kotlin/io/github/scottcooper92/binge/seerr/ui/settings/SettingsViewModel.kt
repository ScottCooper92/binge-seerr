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
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsConsent
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.TelemetryPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** How long the screen waits for the admin groups before it paints without them, as placeholders. */
internal const val SETTLE_MILLIS = 1_000L

/** A read that has not answered yet, told apart from one that answered with nothing (a viewer who may not read the config). */
sealed interface Read<out T> {
    data object Pending : Read<Nothing>

    data class Done<T>(
        val value: T?,
    ) : Read<T>
}

private val <T> Read<T>.valueOrNull: T? get() = (this as? Read.Done<T>)?.value

/**
 * The last answers for the server this process is connected to, so a return to Settings paints the
 * screen it left and refreshes it in place instead of rebuilding it from nothing. Keyed by the
 * server's address and the sign-in: a different server, or a different account on the same one, starts
 * empty rather than showing the old one's rows, which may be ones this viewer may not see.
 */
@Singleton
class SettingsReadCache
    @Inject
    constructor() {
        private var identity: Pair<String, SeerrAuth>? = null

        @Volatile var summary: ConnectionSummary? = null

        @Volatile var server: ServerSummary? = null

        @Volatile var config: Read<ServerConfig>? = null

        @Volatile var offered: Read<List<NotificationSignal>>? = null

        @Synchronized
        fun adopt(identity: Pair<String, SeerrAuth>?) {
            if (identity == this.identity) return
            this.identity = identity
            summary = null
            server = null
            config = null
            offered = null
        }
    }

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
        private val cache: SettingsReadCache,
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

        private suspend fun seeded(): SettingsReadCache =
            cache.also {
                it.adopt(
                    runCatching { connection.current().let { it.baseUrl to it.auth } }.getOrNull(),
                )
            }

        private val summary: Flow<ConnectionSummary?> =
            viewerRefreshed
                .flatMapLatest { flow { emit(runCatching { loader.connection() }.getOrNull()) } }
                .onEach { if (it != null) cache.summary = it }
                .onStart { emit(seeded().summary) }

        private val server: Flow<ServerSummary?> =
            fetchTrigger
                .flatMapLatest { flow { emit(runCatching { loader.server() }.getOrNull()) } }
                .onEach { if (it != null) cache.server = it }
                .onStart { emit(seeded().server) }

        private val config: Flow<Read<ServerConfig>> =
            viewerRefreshed
                .flatMapLatest { flow<Read<ServerConfig>> { emit(Read.Done(loader.config())) } }
                .onEach { cache.config = it }
                .onStart { emit(seeded().config ?: Read.Pending) }

        private val offered: Flow<Read<List<NotificationSignal>>> =
            viewerRefreshed
                .flatMapLatest { flow<Read<List<NotificationSignal>>> { emit(Read.Done(loader.notificationSignals())) } }
                .onEach { cache.offered = it }
                .onStart { emit(seeded().offered ?: Read.Pending) }

        private val enabled: Flow<Set<NotificationSignal>> =
            combine(
                NotificationSignal.entries.map { signal ->
                    prefs.enabled(signal).map { on -> signal.takeIf { on } }
                },
            ) { it.filterNotNull().toSet() }

        private val notifications: Flow<Read<NotificationSettings>> =
            combine(offered, enabled, blockedTrigger, prefs.lastRunMillis, scheduler.nextRunMillis()) { offered, enabled, _, last, next ->
                when (offered) {
                    Read.Pending -> Read.Pending
                    is Read.Done ->
                        Read.Done(
                            offered.value?.let {
                                NotificationSettings(
                                    offered = it,
                                    enabled = enabled.filterTo(mutableSetOf()) { signal -> signal in it },
                                    blocked = !notifier.canPost(),
                                    lastRunMillis = last,
                                    nextRunMillis = next,
                                )
                            },
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

        /** False until [SETTLE_MILLIS] have passed: the admin groups get that long to answer before the screen paints without them. */
        private val settled: Flow<Boolean> =
            flow {
                emit(false)
                delay(SETTLE_MILLIS)
                emit(true)
            }

        val uiState: StateFlow<SettingsUiState> =
            combine(summary, server, config, notifications, app) { summary, server, config, notifications, app ->
                if (summary == null || server == null) {
                    SettingsUiState.Loading
                } else {
                    SettingsUiState.Ready(
                        summary,
                        server,
                        config.valueOrNull,
                        notifications.valueOrNull,
                        app,
                        pending = config is Read.Pending || notifications is Read.Pending,
                    )
                }
            }.combine(settled) { state, settled ->
                // One paint with the groups in it, rather than the screen built in two or three steps.
                if (state is SettingsUiState.Ready && state.pending && !settled) SettingsUiState.Loading else state
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
