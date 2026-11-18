package com.cooper.binge.companion.seerr.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.FeedbackPrefs
import com.cooper.binge.companion.seerr.notifications.DEFAULT_POLL_INTERVAL_MINUTES
import com.cooper.binge.companion.seerr.notifications.FakeBackgroundAccess
import com.cooper.binge.companion.seerr.notifications.FakeNotifier
import com.cooper.binge.companion.seerr.notifications.FakeScheduler
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.cooper.binge.companion.seerr.notifications.seerrForwarding
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrLoginRequest
import com.cooper.binge.companion.seerr.ui.Ticker
import com.cooper.binge.companion.seerr.ui.hub.BingeInstallCheck
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.NoBingeInstallCheck
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ADMIN = 2
private const val REQUEST = 32

/** The This app page's state and writes. Its preferences are on the device; the signals it offers follow the viewer's permissions. */
class ThisAppViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val responses = mutableMapOf<String, () -> FakeResponse>()
    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()
    private val scheduler = FakeScheduler()
    private val notifier = FakeNotifier()
    private val background = FakeBackgroundAccess()
    private lateinit var connection: SeerrConnection
    private lateinit var feedbackPrefs: FeedbackPrefs
    private lateinit var telemetryPrefs: TelemetryPrefs
    private lateinit var prefs: NotificationPrefs

    @Before
    fun setUp() {
        seerr.dispatcher = { request -> responses[request.url.encodedPath]?.invoke() ?: FakeResponse(code = 404) }
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
    }

    private fun serve(
        path: String,
        body: String,
        headers: okhttp3.Headers = headersOf(),
    ) {
        responses[path] =
            { FakeResponse(code = 200, headers = headers.newBuilder().add("Content-Type", "application/json").build(), body = body) }
    }

    private fun server(permissions: Int) {
        serve("/api/v1/status", """{"version":"2.7.0","updateAvailable":true,"commitsBehind":3}""")
        serve("/api/v1/settings/public", """{"applicationTitle":"Family","mediaServerType":2}""")
        serve(
            "/api/v1/auth/me",
            """{"id":1,"displayName":"Scott","permissions":$permissions}""",
            headersOf("Set-Cookie", "connect.sid=s; Path=/"),
        )
        serve(
            "/api/v1/auth/local",
            """{"id":1,"displayName":"Scott","permissions":$permissions}""",
            headersOf("Set-Cookie", "connect.sid=s; Path=/"),
        )
    }

    /** The minute clock read once and never again, so a virtual clock does not spin its loop. */
    private val boundedTicker =
        object : Ticker() {
            override suspend fun await(millis: Long) = awaitCancellation()
        }

    private suspend fun TestScope.viewModel(
        bingeInstallCheck: BingeInstallCheck = NoBingeInstallCheck,
        bingeConnection: BingeConnectionStore = NoBingeConnectionStore,
    ): ThisAppViewModel {
        connection =
            SeerrConnection(
                store =
                    LegacyCredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("s.preferences_pb") },
                        PlainCipher,
                        UnconfinedTestDispatcher(testScheduler),
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
            )
        connection.logIn(seerr.url("/"), SeerrLoginRequest.Local("s@example.com", "pw")).getOrThrow()
        val notificationsStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("n.preferences_pb") }
        prefs = NotificationPrefs(notificationsStore)
        telemetryPrefs =
            TelemetryPrefs(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    folder.newFile("t.preferences_pb")
                },
                crashReportingDefault = true,
            )
        feedbackPrefs = FeedbackPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") })
        val vm =
            ThisAppViewModel(
                feedbackPrefs,
                telemetryPrefs,
                BugReportLinks(),
                SettingsLoader(connection),
                prefs,
                scheduler,
                notifier,
                seerrForwarding(notificationsStore),
                background,
                mainDispatcherRule.dispatcher,
                analytics,
                bingeInstallCheck = bingeInstallCheck,
                bingeConnection = bingeConnection,
                minuteTicker = boundedTicker,
            )
        viewModels.put("this-app", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setScreenVisible(true)
        return vm
    }

    private suspend fun ThisAppViewModel.awaitReady(match: (ThisAppUiState.Ready) -> Boolean = { true }): ThisAppUiState.Ready =
        uiState.first { it is ThisAppUiState.Ready && match(it) } as ThisAppUiState.Ready

    @Test
    fun `the page is ready from the app's own preferences alone, with shake on and crash reports on and usage data off`() =
        runTest {
            server(REQUEST)
            val ready = viewModel().awaitReady()
            val app = ready.app

            assertTrue(app.shakeToReport)
            assertFalse(app.shareUsageData)
            assertTrue(app.sendCrashReports)
            assertTrue(app.bugReportUrl.startsWith("https://github.com/ScottCooper92/app-feedback/issues/new?template=seerr-bug.yml"))
        }

    @Test
    fun `each toggle writes through to its preference, and the shake toggle is reported`() =
        runTest {
            server(REQUEST)
            val vm = viewModel()
            vm.awaitReady()

            vm.setShakeToReport(false)
            vm.awaitReady { !it.app.shakeToReport }
            assertFalse(feedbackPrefs.shakeToReport.first())
            assertEquals(listOf("shake_to_report_changed" to mapOf("enabled" to false)), analytics.events)

            vm.setShareUsageData(true)
            vm.awaitReady { it.app.shareUsageData }
            assertEquals(AnalyticsConsent.GRANTED, telemetryPrefs.analyticsConsent.first())

            vm.setSendCrashReports(false)
            vm.awaitReady { !it.app.sendCrashReports }
            assertFalse(telemetryPrefs.crashReportingEnabled.first())
        }

    @Test
    fun `the Binge row reads install on each arrival, then whether Binge has connected`() =
        runTest {
            server(REQUEST)
            var installed = false
            val handshaked = MutableStateFlow(false)
            val store =
                object : BingeConnectionStore by NoBingeConnectionStore {
                    override val hasConnected = handshaked
                }
            val vm = viewModel(bingeInstallCheck = { installed }, bingeConnection = store)

            assertEquals(BingeStatus.NotInstalled, vm.awaitReady().app.binge)

            installed = true
            vm.setScreenVisible(true)
            assertEquals(BingeStatus.NotConnected, vm.awaitReady { it.app.binge == BingeStatus.NotConnected }.app.binge)

            handshaked.value = true
            assertEquals(BingeStatus.Connected, vm.awaitReady { it.app.binge == BingeStatus.Connected }.app.binge)
        }

    @Test
    fun `the poll's toggles are offered by permission, written to the prefs, and read with the blocked state and the schedule`() =
        runTest {
            server(REQUEST)
            notifier.canPost = false
            scheduler.nextRun.value = 4_000L
            val vm = viewModel()

            val offered = checkNotNull(vm.awaitReady { it.notifications != null }.notifications)
            assertEquals(
                listOf(NotificationSignal.RequestAvailable, NotificationSignal.RequestApproved, NotificationSignal.RequestDeclined),
                offered.offered,
            )
            assertTrue(offered.enabled.isEmpty())
            assertTrue(offered.blocked)
            assertEquals(4_000L, offered.nextRunMillis)

            vm.setSignal(NotificationSignal.RequestApproved, true)
            val on = checkNotNull(vm.awaitReady { it.notifications?.enabled?.isNotEmpty() == true }.notifications)
            assertEquals(setOf(NotificationSignal.RequestApproved), on.enabled)
            assertTrue(prefs.isEnabled(NotificationSignal.RequestApproved))
            assertEquals(
                listOf("notification_signal_changed" to mapOf("signal" to "RequestApproved", "enabled" to true)),
                analytics.events,
            )

            notifier.canPost = true
            vm.recheckNotificationAccess()
            assertNotNull(vm.awaitReady { it.notifications?.blocked == false }.notifications)
        }

    @Test
    fun `the poll's interval starts at the default, is changed by the page, and reads back from the prefs`() =
        runTest {
            server(REQUEST)
            val vm = viewModel()

            assertEquals(
                DEFAULT_POLL_INTERVAL_MINUTES,
                checkNotNull(vm.awaitReady { it.notifications != null }.notifications).intervalMinutes,
            )

            vm.setPollInterval(60)
            assertEquals(60, checkNotNull(vm.awaitReady { it.notifications?.intervalMinutes == 60 }.notifications).intervalMinutes)
            assertEquals(60, prefs.pollIntervalMinutes.first())
        }

    @Test
    fun `an app Android holds back is reported as restricted, and as unrestricted again once the user changes it`() =
        runTest {
            server(REQUEST)
            background.unrestricted = false
            val vm = viewModel()

            assertFalse(checkNotNull(vm.awaitReady { it.notifications != null }.notifications).unrestricted)

            // What the user decided in Android's settings is read when they come back to the page.
            background.unrestricted = true
            vm.recheckNotificationAccess()
            assertTrue(checkNotNull(vm.awaitReady { it.notifications?.unrestricted == true }.notifications).unrestricted)
        }

    @Test
    fun `a signal left enabled in prefs from before a permission downgrade is not reported as enabled`() =
        runTest {
            server(REQUEST)
            val vm = viewModel()
            vm.awaitReady { it.notifications != null }
            prefs.setEnabled(NotificationSignal.PendingRequests, true)

            val notifications = checkNotNull(vm.awaitReady { it.notifications != null }.notifications)

            assertTrue(NotificationSignal.PendingRequests !in notifications.offered)
            assertTrue(notifications.enabled.isEmpty())
        }

    @Test
    fun `a moderator is offered the feeds too`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            assertEquals(NotificationSignal.entries, checkNotNull(vm.awaitReady { it.notifications != null }.notifications).offered)
        }
}
