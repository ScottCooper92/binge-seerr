package com.cooper.binge.companion.seerr.flags

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

    private var files = 0

    private suspend fun TestScope.prefs(stored: Boolean?): DiscoveryPrefs {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d${files++}.preferences_pb") }
        return DiscoveryPrefs(store).also { if (stored != null) it.setEnabled(stored) }
    }

    private fun TestScope.gate(
        flag: Boolean,
        prefs: DiscoveryPrefs,
    ) = PrefsDiscoveryGate(
        FixedFeatureFlags(Flags.of(DiscoveryEnabled to flag)),
        prefs,
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
    )

    @Test
    fun `the preference defaults to on`() =
        runTest {
            assertTrue(prefs(stored = null).enabled.first())
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
                val gate = gate(flag, prefs(stored = pref))
                assertEquals("flag=$flag pref=$pref", expected, gate.resolved.first { it != null })
            }
        }

    @Test
    fun `the row is offered only while the remote flag is on`() =
        runTest {
            assertFalse(gate(false, prefs(null)).offered.first())
            assertTrue(gate(true, prefs(null)).offered.first())
        }

    @Test
    fun `an opted-out user never observes discovery on, so a first value is not mistaken for a toggle`() =
        runTest {
            val gate = gate(flag = true, prefs = prefs(stored = false))
            val seen = mutableListOf<Boolean?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gate.resolved.collect { seen += it } }

            assertEquals(false, gate.resolved.first { it != null })
            assertFalse("the value is false from its first non-null state: $seen", seen.any { it == true })
        }

    @Test
    fun `before it resolves the flag alone is the fallback and current reads it`() =
        runTest {
            val gate = gate(flag = true, prefs = prefs(null))
            assertTrue(gate.fallback)
            assertTrue(gate.current())
        }

    @Test
    fun `turning the preference off turns discovery off and on again restores it`() =
        runTest {
            val gate = gate(flag = true, prefs = prefs(null))
            assertEquals(true, gate.resolved.first { it != null })
            gate.setPreference(false)
            assertEquals(false, gate.resolved.first { it == false })
            gate.setPreference(true)
            assertEquals(true, gate.resolved.first { it == true })
        }
}
