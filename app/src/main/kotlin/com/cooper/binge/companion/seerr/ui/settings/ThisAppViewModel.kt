package com.cooper.binge.companion.seerr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.FeedbackPrefs
import com.cooper.binge.companion.seerr.flags.FeatureFlags
import com.cooper.binge.companion.seerr.flags.FixedFeatureFlags
import com.cooper.binge.companion.seerr.flags.Flags
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.NoOpAnalytics
import com.cooper.binge.companion.seerr.telemetry.TelemetryPrefs
import com.cooper.binge.companion.seerr.ui.hub.BingeInstallCheck
import com.cooper.binge.companion.seerr.ui.hub.NoBingeInstallCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** This app's page: ready as soon as its own preferences have been read, with no server involved. */
sealed interface ThisAppUiState {
    data object Loading : ThisAppUiState

    data class Ready(
        val app: AppSettings,
    ) : ThisAppUiState
}

/**
 * The This app page: Binge, bug reporting and what the app may report on its own. It reads nothing from the server, so
 * it opens at once, offline, and for a user the server lets see nothing else. The tip jar sits beside it in its own ViewModel.
 */
@HiltViewModel
class ThisAppViewModel
    @Inject
    constructor(
        feedbackPrefs: FeedbackPrefs,
        telemetryPrefs: TelemetryPrefs,
        bugReportLinks: BugReportLinks,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        analytics: Analytics = NoOpAnalytics,
        bingeInstallCheck: BingeInstallCheck = NoBingeInstallCheck,
        bingeConnection: BingeConnectionStore = NoBingeConnectionStore,
        featureFlags: FeatureFlags = FixedFeatureFlags(Flags.AllOn),
    ) : ViewModel() {
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
            )

        val uiState: StateFlow<ThisAppUiState> =
            source.settings
                .map<AppSettings, ThisAppUiState> { ThisAppUiState.Ready(it) }
                .stateIn(viewModelScope, SharingStarted.Lazily, ThisAppUiState.Loading)

        /** Re-read on every arrival, so Binge installed while the page was away shows on return. */
        fun setScreenVisible(visible: Boolean) {
            if (visible) source.recheckBinge()
        }

        fun setShakeToReport(enabled: Boolean) = source.setShakeToReport(enabled)

        fun setShareUsageData(enabled: Boolean) = source.setShareUsageData(enabled)

        fun setSendCrashReports(enabled: Boolean) = source.setSendCrashReports(enabled)
    }
