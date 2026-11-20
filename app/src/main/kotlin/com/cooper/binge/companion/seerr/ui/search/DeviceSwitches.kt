package com.cooper.binge.companion.seerr.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.ShakeToReportSetting
import com.cooper.binge.companion.seerr.ui.hub.BingeInstallCheck
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.AppSettingsSource
import com.cooper.homelab.core.flags.BooleanPreference
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import com.cooper.homelab.core.uiandroid.search.SearchToggle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * One on/off setting that lives on this device, on the This app page: how to read it from [AppSettings] and how to write it through
 * [AppSettingsSource], the page's own write. There is no server and no draft, so a flip is one local write.
 */
internal class DeviceSwitch(
    val read: (AppSettings) -> Boolean,
    val write: (AppSettingsSource, Boolean) -> Unit,
)

/** The switches on the This app page, by [SettingRows] key. Everything else there opens a page or an app, and keeps navigating. */
internal val deviceSwitches: Map<String, DeviceSwitch> =
    mapOf(
        SettingRows.APP_SHAKE_TO_REPORT to DeviceSwitch({ it.shakeToReport }, AppSettingsSource::setShakeToReport),
        SettingRows.APP_SHARE_USAGE_DATA to DeviceSwitch({ it.shareUsageData }, AppSettingsSource::setShareUsageData),
        SettingRows.APP_SEND_CRASH_REPORTS to DeviceSwitch({ it.sendCrashReports }, AppSettingsSource::setSendCrashReports),
    )

/** The toggle a result for [this] carries: the value the page shows, and a write that is the page's own. [set] is that write. */
internal fun DeviceSwitch.toggle(
    app: AppSettings,
    set: (Boolean) -> Unit,
): SearchToggle = SearchToggle(checked = read(app), onCheckedChange = set, enabled = true, loading = false)

/**
 * What a result for a This app setting reads and writes. It is [AppSettingsSource], as the This app page's ViewModel builds it,
 * and not that ViewModel: that one also loads the server's notification settings, which the search has no use for.
 */
@HiltViewModel
internal class AppSwitchesViewModel
    @Inject
    constructor(
        @ShakeToReportSetting feedbackPrefs: BooleanPreference,
        telemetryPrefs: TelemetryPrefs,
        bugReportLinks: BugReportLinks,
        bingeInstallCheck: BingeInstallCheck,
        bingeConnection: BingeConnectionStore,
        featureFlags: FeatureFlags,
        analytics: Analytics,
        @IoDispatcher dispatcher: CoroutineDispatcher,
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

        /** The page's own settings, or null until the first read, while a row stays a plain navigation row. */
        val settings: StateFlow<AppSettings?> = source.settings.stateIn(viewModelScope, SharingStarted.Lazily, null)

        fun set(
            switch: DeviceSwitch,
            on: Boolean,
        ) = switch.write(source, on)
    }
