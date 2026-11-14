package com.cooper.binge.companion.seerr.ui.settings

import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.FeedbackPrefs
import com.cooper.binge.companion.seerr.flags.FeatureFlags
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsConsent
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.TelemetryPrefs
import com.cooper.binge.companion.seerr.ui.hub.BingeInstallCheck
import com.cooper.binge.companion.seerr.ui.hub.bingeStatusOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * This app's own settings and where Binge stands: what the This app page reads and writes, and what the TV settings sheet
 * reads. Each screen's ViewModel builds one over its own [scope], so none of them carries the other's server fetches.
 */
internal class AppSettingsSource(
    private val feedbackPrefs: FeedbackPrefs,
    private val telemetryPrefs: TelemetryPrefs,
    bugReportLinks: BugReportLinks,
    private val bingeInstallCheck: BingeInstallCheck,
    bingeConnection: BingeConnectionStore,
    featureFlags: FeatureFlags,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    /** Re-read on every arrival, so Binge installed while this screen was away shows on return. */
    private val bingeInstalled = MutableStateFlow(bingeInstallCheck.isInstalled())

    val settings: Flow<AppSettings> =
        combine(
            feedbackPrefs.shakeToReport,
            telemetryPrefs.analyticsConsent,
            telemetryPrefs.crashReportingEnabled,
            combine(bingeInstalled, bingeConnection.hasConnected, featureFlags.flags, ::bingeStatusOf),
        ) { shake, consent, crashes, binge ->
            AppSettings(
                bugReportUrl = bugReportLinks.url(),
                shakeToReport = shake,
                shareUsageData = consent == AnalyticsConsent.GRANTED,
                sendCrashReports = crashes,
                binge = binge,
            )
        }

    fun recheckBinge() {
        bingeInstalled.value = bingeInstallCheck.isInstalled()
    }

    fun setShakeToReport(enabled: Boolean) {
        analytics.event(AnalyticsEvents.SHAKE_TO_REPORT_CHANGED, mapOf(AnalyticsEvents.PARAM_ENABLED to enabled))
        scope.launch(dispatcher) { feedbackPrefs.setShakeToReport(enabled) }
    }

    fun setShareUsageData(enabled: Boolean) {
        scope.launch(dispatcher) { telemetryPrefs.setAnalyticsGranted(enabled) }
    }

    fun setSendCrashReports(enabled: Boolean) {
        scope.launch(dispatcher) { telemetryPrefs.setCrashReportingEnabled(enabled) }
    }
}
