package com.cooper.binge.companion.seerr.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.FeedbackPrefs
import com.cooper.binge.companion.seerr.telemetry.AnalyticsConsent
import com.cooper.binge.companion.seerr.telemetry.TelemetryPrefs
import com.cooper.binge.companion.seerr.ui.hub.BingeInstallCheck
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.NoBingeInstallCheck
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The This app page's state and writes: no server is involved, so none is set up. */
class ThisAppViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()
    private lateinit var feedbackPrefs: FeedbackPrefs
    private lateinit var telemetryPrefs: TelemetryPrefs

    @After
    fun tearDown() = viewModels.clear()

    private fun TestScope.viewModel(
        bingeInstallCheck: BingeInstallCheck = NoBingeInstallCheck,
        bingeConnection: BingeConnectionStore = NoBingeConnectionStore,
    ): ThisAppViewModel {
        telemetryPrefs = TelemetryPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("t.preferences_pb") })
        feedbackPrefs = FeedbackPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") })
        val vm =
            ThisAppViewModel(
                feedbackPrefs,
                telemetryPrefs,
                BugReportLinks(),
                mainDispatcherRule.dispatcher,
                analytics,
                bingeInstallCheck = bingeInstallCheck,
                bingeConnection = bingeConnection,
            )
        viewModels.put("this-app", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setScreenVisible(true)
        return vm
    }

    private suspend fun ThisAppViewModel.awaitReady(match: (AppSettings) -> Boolean = { true }): AppSettings =
        (uiState.first { it is ThisAppUiState.Ready && match(it.app) } as ThisAppUiState.Ready).app

    @Test
    fun `the page is ready from the app's own preferences alone, with shake on and crash reports on and usage data off`() =
        runTest {
            val app = viewModel().awaitReady()

            assertTrue(app.shakeToReport)
            assertFalse(app.shareUsageData)
            assertTrue(app.sendCrashReports)
            assertTrue(app.bugReportUrl.startsWith("https://github.com/ScottCooper92/binge-seerr/issues/new?template=bug.yml"))
        }

    @Test
    fun `each toggle writes through to its preference, and the shake toggle is reported`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.setShakeToReport(false)
            vm.awaitReady { !it.shakeToReport }
            assertFalse(feedbackPrefs.shakeToReport.first())
            assertEquals(listOf("shake_to_report_changed" to mapOf("enabled" to false)), analytics.events)

            vm.setShareUsageData(true)
            vm.awaitReady { it.shareUsageData }
            assertEquals(AnalyticsConsent.GRANTED, telemetryPrefs.analyticsConsent.first())

            vm.setSendCrashReports(false)
            vm.awaitReady { !it.sendCrashReports }
            assertFalse(telemetryPrefs.crashReportingEnabled.first())
        }

    @Test
    fun `the Binge row reads install on each arrival, then whether Binge has connected`() =
        runTest {
            var installed = false
            val handshaked = MutableStateFlow(false)
            val store =
                object : BingeConnectionStore by NoBingeConnectionStore {
                    override val hasConnected = handshaked
                }
            val vm = viewModel(bingeInstallCheck = { installed }, bingeConnection = store)

            assertEquals(BingeStatus.NotInstalled, vm.awaitReady().binge)

            installed = true
            vm.setScreenVisible(true)
            assertEquals(BingeStatus.NotConnected, vm.awaitReady { it.binge == BingeStatus.NotConnected }.binge)

            handshaked.value = true
            assertEquals(BingeStatus.Connected, vm.awaitReady { it.binge == BingeStatus.Connected }.binge)
        }
}
