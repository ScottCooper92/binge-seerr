package com.cooper.binge.companion.seerr.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.NoBingeConnectionStore
import com.cooper.binge.companion.seerr.feedback.BugReportLinks
import com.cooper.binge.companion.seerr.feedback.FeedbackPrefs
import com.cooper.binge.companion.seerr.flags.DiscoveryEnabled
import com.cooper.binge.companion.seerr.flags.DiscoveryPrefs
import com.cooper.binge.companion.seerr.ui.hub.NoBingeInstallCheck
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.FlagPreferenceGate
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The Discovery row's value on This app's settings: hidden while the remote flag is off, the preference otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsSourceDiscoveryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val analytics = RecordingAnalytics()

    private fun kotlinx.coroutines.test.TestScope.source(flag: Boolean): Pair<AppSettingsSource, DiscoveryPrefs> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val flags = FixedFeatureFlags(Flags.of(DiscoveryEnabled to flag))
        val discovery = DiscoveryPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d.preferences_pb") })
        val feedback = FeedbackPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("f.preferences_pb") })
        val telemetry =
            TelemetryPrefs(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("t.preferences_pb") },
                crashReportingDefault = true,
            )
        val source =
            AppSettingsSource(
                feedback,
                telemetry,
                BugReportLinks(),
                NoBingeInstallCheck,
                NoBingeConnectionStore,
                flags,
                analytics,
                backgroundScope,
                dispatcher,
                FlagPreferenceGate(flags, DiscoveryEnabled, discovery.enabled, discovery::setEnabled, backgroundScope),
            )
        return source to discovery
    }

    @Test
    fun `the row is hidden while the remote flag is off`() =
        runTest {
            val (source, _) = source(flag = false)
            assertNull(source.settings.first().discovery)
        }

    @Test
    fun `with the flag on the row reads the preference, which starts on`() =
        runTest {
            val (source, prefs) = source(flag = true)
            assertEquals(true, source.settings.first().discovery)

            prefs.setEnabled(false)
            assertEquals(false, source.settings.first { it.discovery == false }.discovery)
        }

    @Test
    fun `turning it off writes the preference and reports the change`() =
        runTest {
            val (source, prefs) = source(flag = true)

            source.setDiscovery(false)

            assertEquals(false, prefs.enabled.first { !it })
            assertEquals(listOf("discovery_changed" to mapOf("enabled" to false)), analytics.events)
        }
}
