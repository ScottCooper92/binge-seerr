package com.cooper.binge.companion.seerr.flags

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.cooper.homelab.core.flags.DataStoreFlagOverrides
import com.cooper.homelab.core.flags.FlagOverrides
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.NoFlagOverrides
import com.cooper.homelab.core.flags.NoRemoteFlagSource
import com.cooper.homelab.core.flags.RemoteFeatureFlags
import com.cooper.homelab.core.flags.RemoteFlagSource
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** This app's own flags, which ride the shared registry: the keys they have always had, the same override store, and no remote value for the switches. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppFlagsTest {
    private class FakeSource(
        private val remote: Flags,
    ) : RemoteFlagSource {
        override fun current(): Flags = remote

        override suspend fun fetch(): Flags = remote
    }

    private fun TestScope.flags(
        source: RemoteFlagSource = NoRemoteFlagSource,
        overrides: FlagOverrides = NoFlagOverrides,
        initial: Map<String, Boolean> = emptyMap(),
    ) = RemoteFeatureFlags(
        source,
        overrides,
        TestScope(UnconfinedTestDispatcher(testScheduler)),
        UnconfinedTestDispatcher(testScheduler),
        initial,
    )

    @Test
    fun `the keys are the Remote Config names the app has always used`() {
        assertEquals("discovery_enabled", DiscoveryEnabled.key)
        assertEquals("search_toggles_enabled", SearchToggles.key)
        assertEquals(listOf(SharedFlags.BingePromotion, SharedFlags.ReviewPrompt, DiscoveryEnabled, SharedSetupEnabled), RemoteFlagKeys)
        assertEquals("shared_setup_enabled", SharedSetupEnabled.key)
    }

    @Test
    fun `the search switches are not read from Remote Config`() {
        assertFalse(SearchToggles in RemoteFlagKeys)
        assertTrue(SearchToggles in AllFlagKeys)
    }

    @Test
    fun `discovery is off with nothing behind it and follows the remote value once it arrives`() =
        runTest {
            val off = flags()
            off.refresh()
            assertFalse(off.flags.value[DiscoveryEnabled])

            val on = flags(FakeSource(Flags.of(DiscoveryEnabled to true)))
            assertFalse(on.flags.value[DiscoveryEnabled])
            on.refresh()
            assertTrue(on.flags.value[DiscoveryEnabled])
        }

    @Test
    fun `an override wins over the remote discovery value and clearing it gives the remote value back`() =
        runTest {
            val store = InMemoryDataStore()
            val overrides = DataStoreFlagOverrides(store)
            val flags = flags(FakeSource(Flags.of(DiscoveryEnabled to true)), overrides)
            flags.refresh()
            assertTrue(flags.flags.value[DiscoveryEnabled])

            overrides.set(DiscoveryEnabled, false)
            assertFalse(flags.flags.first { !it[DiscoveryEnabled] }[DiscoveryEnabled])

            overrides.set(DiscoveryEnabled, null)
            assertTrue(flags.flags.first { it[DiscoveryEnabled] }[DiscoveryEnabled])
        }

    @Test
    fun `the search switches are on only through an override, whatever Remote Config says`() =
        runTest {
            val none = flags(FakeSource(Flags.of(SharedFlags.BingePromotion to true)))
            none.refresh()
            assertFalse(none.flags.value[SearchToggles])

            val overrides = DataStoreFlagOverrides(InMemoryDataStore())
            val flags = flags(overrides = overrides)
            assertFalse(flags.flags.value[SearchToggles])
            overrides.set(SearchToggles, true)
            assertTrue(flags.flags.first { it[SearchToggles] }[SearchToggles])
        }

    @Test
    fun `a cold start with an override already on disk reads it before any refresh`() =
        runTest {
            val store = InMemoryDataStore(mutablePreferencesOf(booleanPreferencesKey("override_discovery_enabled") to true))
            val overrides = DataStoreFlagOverrides(store)
            assertTrue(flags(overrides = overrides, initial = overrides.values.first()).flags.value[DiscoveryEnabled])
        }

    @Test
    fun `overrides an earlier version wrote read back and the entries written are the same names`() =
        runTest {
            val seeded =
                InMemoryDataStore(
                    mutablePreferencesOf(
                        booleanPreferencesKey("override_binge_promotion_enabled") to true,
                        booleanPreferencesKey("override_discovery_enabled") to false,
                        booleanPreferencesKey("override_search_toggles_enabled") to true,
                    ),
                )
            assertEquals(
                mapOf("binge_promotion_enabled" to true, "discovery_enabled" to false, "search_toggles_enabled" to true),
                DataStoreFlagOverrides(seeded).values.first(),
            )

            val written = InMemoryDataStore()
            DataStoreFlagOverrides(written).apply {
                set(DiscoveryEnabled, true)
                set(SearchToggles, true)
            }
            assertEquals(
                setOf("override_discovery_enabled", "override_search_toggles_enabled"),
                written.data.value
                    .asMap()
                    .keys
                    .map { it.name }
                    .toSet(),
            )
        }
}
