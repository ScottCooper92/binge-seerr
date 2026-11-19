package com.cooper.binge.companion.seerr.flags

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Discovery is the remote flag AND the user's own preference, read from this one place. */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryGateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.gate(
        flag: Boolean,
        flags: FeatureFlags = FixedFeatureFlags(Flags.of(DiscoveryEnabled to flag)),
    ): Pair<PrefsDiscoveryGate, DiscoveryPrefs> {
        val prefs = DiscoveryPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d.preferences_pb") })
        return PrefsDiscoveryGate(flags, prefs, kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))) to prefs
    }

    @Test
    fun `the preference defaults to on`() =
        runTest {
            val (_, prefs) = gate(flag = true)
            assertTrue(prefs.enabled.first())
        }

    @Test
    fun `the four flag and preference cases`() =
        runTest {
            val cases =
                listOf(
                    Triple(false, false, false),
                    Triple(false, true, false),
                    Triple(true, false, false),
                    Triple(true, true, true),
                )
            for ((flag, pref, expected) in cases) {
                val (gate, prefs) = gate(flag)
                prefs.setEnabled(pref)
                assertEquals("flag=$flag pref=$pref", expected, gate.enabled.first { it == expected })
            }
        }

    @Test
    fun `the row is offered only while the remote flag is on`() =
        runTest {
            assertFalse(gate(flag = false).first.offered.first())
            assertTrue(gate(flag = true).first.offered.first())
        }

    @Test
    fun `turning the preference off turns discovery off and on again restores it`() =
        runTest {
            val (gate, _) = gate(flag = true)
            assertTrue(gate.enabled.value)
            gate.setPreference(false)
            assertFalse(gate.enabled.first { !it })
            gate.setPreference(true)
            assertTrue(gate.enabled.first { it })
        }
}
