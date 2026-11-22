package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.shakeToReportPreference
import com.cooper.binge.companion.seerr.ui.hub.NoBingeInstallCheck
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.homelab.core.flags.BooleanPreference
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import com.cooper.homelab.core.testing.RecordingAnalytics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A result for a This app setting carries a switch over the device's own preferences, as the This app page does: the value the page
 * shows and the page's own write, with no server in between.
 */
class DeviceSwitchTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()
    private lateinit var feedbackPrefs: BooleanPreference
    private lateinit var telemetryPrefs: TelemetryPrefs

    @After
    fun tearDown() = viewModels.clear()

    private fun TestScope.viewModel(): AppSwitchesViewModel {
        feedbackPrefs =
            shakeToReportPreference(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") })
        telemetryPrefs =
            TelemetryPrefs(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("t.preferences_pb") },
                crashReportingDefault = true,
            )
        val vm =
            AppSwitchesViewModel(
                feedbackPrefs,
                telemetryPrefs,
                BugReportLinks(),
                NoBingeInstallCheck,
                NoBingeConnectionStore,
                FixedFeatureFlags(Flags.of()),
                analytics,
                mainDispatcherRule.dispatcher,
            )
        viewModels.put("switches", vm)
        backgroundScope.launch { vm.settings.collect {} }
        return vm
    }

    private suspend fun AppSwitchesViewModel.ready(where: (AppSettings) -> Boolean = { true }): AppSettings =
        checkNotNull(settings.first { it != null && where(it) })

    @Test
    fun `a switch shows the value the page shows for its setting`() =
        runTest {
            val vm = viewModel()
            val app = vm.ready()

            assertTrue(deviceSwitches.getValue(SettingRows.APP_SHAKE_TO_REPORT).toggle(app) {}.checked)
            assertFalse(deviceSwitches.getValue(SettingRows.APP_SHARE_USAGE_DATA).toggle(app) {}.checked)
            assertTrue(deviceSwitches.getValue(SettingRows.APP_SEND_CRASH_REPORTS).toggle(app) {}.checked)
        }

    @Test
    fun `a flip writes the page's own preference and reads back as the page does`() =
        runTest {
            val vm = viewModel()
            val shake = deviceSwitches.getValue(SettingRows.APP_SHAKE_TO_REPORT)
            val usage = deviceSwitches.getValue(SettingRows.APP_SHARE_USAGE_DATA)
            val crashes = deviceSwitches.getValue(SettingRows.APP_SEND_CRASH_REPORTS)

            shake.toggle(vm.ready()) { vm.set(shake, it) }.onCheckedChange(false)
            vm.ready { !it.shakeToReport }
            assertFalse(feedbackPrefs.value.first())
            assertEquals(listOf("shake_to_report_changed" to mapOf("enabled" to false)), analytics.events)

            usage.toggle(vm.ready()) { vm.set(usage, it) }.onCheckedChange(true)
            vm.ready { it.shareUsageData }
            assertEquals(AnalyticsConsent.GRANTED, telemetryPrefs.analyticsConsent.first())

            crashes.toggle(vm.ready()) { vm.set(crashes, it) }.onCheckedChange(false)
            vm.ready { !it.sendCrashReports }
            assertFalse(telemetryPrefs.crashReportingEnabled.first())
        }

    @Test
    fun `a device switch is always allowed and never loading`() =
        runTest {
            val toggle = deviceSwitches.getValue(SettingRows.APP_SHAKE_TO_REPORT).toggle(viewModel().ready()) {}

            assertTrue(toggle.enabled)
            assertFalse(toggle.loading)
        }

    @Test
    fun `only the three plain on and off rows are switches`() {
        assertEquals(
            setOf(SettingRows.APP_SHAKE_TO_REPORT, SettingRows.APP_SHARE_USAGE_DATA, SettingRows.APP_SEND_CRASH_REPORTS),
            deviceSwitches.keys,
        )
    }
}
