package com.cooper.binge.companion.seerr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.FeedbackPrefs
import com.cooper.binge.companion.seerr.notifications.BackgroundAccess
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.notifications.NotificationScheduler
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.cooper.binge.companion.seerr.notifications.SeerrForwarding
import com.cooper.binge.companion.seerr.notifications.SeerrNotifier
import com.cooper.binge.companion.seerr.ui.Ticker
import com.cooper.binge.companion.seerr.ui.hub.BingeInstallCheck
import com.cooper.binge.companion.seerr.ui.hub.NoBingeInstallCheck
import com.cooper.binge.companion.seerr.ui.minuteClock
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.NoPreferenceGate
import com.cooper.homelab.core.flags.PreferenceGate
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** This app's page: ready as soon as its own preferences have been read, with the notification group joining once the server has said what to offer. */
sealed interface ThisAppUiState {
    data object Loading : ThisAppUiState

    data class Ready(
        val app: AppSettings,
        /** Null until the signals this viewer may turn on have been read. */
        val notifications: NotificationSettings? = null,
        /** What the poll's last and next runs are worded against, moved on each minute while the page shows. */
        val now: Long = 0L,
    ) : ThisAppUiState
}

/**
 * The This app page: Binge, bug reporting, what the app may report on its own, and its own notifications. Its preferences are
 * on the device, so the page opens at once and offline; the notification group waits only for the server to say which signals
 * this viewer may use. The tip jar sits beside it in its own ViewModel.
 */
@HiltViewModel
class ThisAppViewModel
    @Inject
    constructor(
        feedbackPrefs: FeedbackPrefs,
        telemetryPrefs: TelemetryPrefs,
        bugReportLinks: BugReportLinks,
        loader: SettingsLoader,
        prefs: NotificationPrefs,
        scheduler: NotificationScheduler,
        notifier: SeerrNotifier,
        forwarding: SeerrForwarding,
        background: BackgroundAccess,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        analytics: Analytics = NoOpAnalytics,
        bingeInstallCheck: BingeInstallCheck = NoBingeInstallCheck,
        bingeConnection: BingeConnectionStore = NoBingeConnectionStore,
        featureFlags: FeatureFlags = FixedFeatureFlags(Flags.of(SharedFlags.BingePromotion to true, SharedFlags.ReviewPrompt to true)),
        private val minuteTicker: Ticker = Ticker(),
        discoveryGate: PreferenceGate = NoPreferenceGate,
    ) : ViewModel() {
        internal var clock: () -> Long = System::currentTimeMillis

        /** What [ThisAppUiState.Ready.now] reads: moved on each minute by [ticker], which runs while the page is showing. */
        private val now = MutableStateFlow(clock())
        private var ticker: Job? = null

        private val source =
            AppSettingsSource(
                feedbackPrefs,
                telemetryPrefs,
                bugReportLinks,
                bingeInstallCheck,
                bingeConnection,
                featureFlags,
                analytics,
                viewModelScope,
                dispatcher,
                discoveryGate,
            )

        private val poll =
            PollNotificationsSource(loader, prefs, scheduler, notifier, forwarding, background, analytics, viewModelScope, dispatcher, now)

        val uiState: StateFlow<ThisAppUiState> =
            combine(source.settings, poll.settings.onStart { emit(null) }, now) { app, notifications, now ->
                ThisAppUiState.Ready(app, notifications, now) as ThisAppUiState
            }.stateIn(viewModelScope, SharingStarted.Lazily, ThisAppUiState.Loading)

        /** Re-read on every arrival, so Binge installed or a permission changed while the page was away shows on return. */
        fun setScreenVisible(visible: Boolean) {
            ticker?.cancel()
            ticker = null
            if (visible) {
                source.recheckBinge()
                poll.refresh()
                // The poll's "next run in 20 minutes" counts down while the page shows it, and no longer.
                ticker = viewModelScope.launch(dispatcher) { minuteClock(clock, minuteTicker).collect { now.value = it } }
            }
        }

        /** After the system's notification page or the permission prompt: what they decided is only readable, not observable. */
        fun recheckNotificationAccess() = poll.recheckAccess()

        fun setSignal(
            signal: NotificationSignal,
            value: Boolean,
        ) = poll.setSignal(signal, value)

        fun setPollInterval(minutes: Int) = poll.setInterval(minutes)

        fun setDiscovery(enabled: Boolean) = source.setDiscovery(enabled)

        fun setShakeToReport(enabled: Boolean) = source.setShakeToReport(enabled)

        fun setShareUsageData(enabled: Boolean) = source.setShareUsageData(enabled)

        fun setSendCrashReports(enabled: Boolean) = source.setSendCrashReports(enabled)
    }
