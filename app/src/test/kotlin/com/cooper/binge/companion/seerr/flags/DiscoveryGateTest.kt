package com.cooper.binge.companion.seerr.flags

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.FlagPreferenceGate
import com.cooper.homelab.core.flags.Flags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Discovery setting's wiring into core's shared gate: this app's flag and its own preference reach it, and a write goes back to
 * the preference. The gate's own rules (the four combined cases, nothing resolved before the preference is read) are tested in core.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryGateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.wiring(flag: Boolean): Pair<FlagPreferenceGate, DiscoveryPrefs> {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d.preferences_pb") }
        val prefs = DiscoveryPrefs(store)
        val flags = FixedFeatureFlags(Flags.of(DiscoveryEnabled to flag))
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val gate = FlagPreferenceGate(flags, DiscoveryEnabled, prefs.enabled, prefs::setEnabled, scope)
        return gate to prefs
    }

    @Test
    fun `the preference defaults to on, and the gate offers the row only while the flag is on`() =
        runTest {
            val (on, prefs) = wiring(flag = true)
            assertTrue(prefs.enabled.first())
            assertTrue(on.offered.first())
            assertEquals(true, on.resolved.first { it != null })
        }

    @Test
    fun `with the flag off nothing is offered and discovery is off`() =
        runTest {
            val (off, _) = wiring(flag = false)
            assertFalse(off.offered.first())
            assertEquals(false, off.resolved.first { it != null })
        }

    @Test
    fun `setting the preference writes it back to this app's store`() =
        runTest {
            val (gate, prefs) = wiring(flag = true)

            gate.setPreference(false)

            assertFalse(prefs.enabled.first { !it })
            assertEquals(false, gate.resolved.first { it == false })
        }
}
