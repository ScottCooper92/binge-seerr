package com.cooper.binge.companion.seerr.flags

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryFlagTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeSource(
        var cached: Boolean = false,
        var fetched: Boolean? = null,
    ) : DiscoveryFlagSource {
        override fun current() = cached

        override suspend fun fetch() = fetched
    }

    private class FakeOverride(
        initial: Boolean? = null,
    ) : DiscoveryFlagOverride {
        val state = MutableStateFlow(initial)
        override val value = state

        override suspend fun set(value: Boolean?) {
            state.value = value
        }
    }

    private fun TestScope.flag(
        source: DiscoveryFlagSource,
        override: DiscoveryFlagOverride = NoDiscoveryFlagOverride,
        initialOverride: Boolean? = null,
    ) = RemoteDiscoveryFlag(
        source,
        override,
        TestScope(UnconfinedTestDispatcher(testScheduler)),
        UnconfinedTestDispatcher(testScheduler),
        initialOverride,
    )

    @Test
    fun `the flag is off with nothing cached and nothing fetched`() =
        runTest {
            val flag = flag(FakeSource())
            advanceUntilIdle()
            flag.refresh()
            assertFalse(flag.enabled.value)
        }

    @Test
    fun `a cached remote value is on at the first read`() =
        runTest {
            assertTrue(flag(FakeSource(cached = true)).enabled.value)
        }

    @Test
    fun `a fetched value replaces the cached one`() =
        runTest {
            val flag = flag(FakeSource(cached = false, fetched = true))
            flag.refresh()
            advanceUntilIdle()
            assertTrue(flag.enabled.value)
        }

    @Test
    fun `a failed fetch keeps the cached value`() =
        runTest {
            val flag = flag(FakeSource(cached = true, fetched = null))
            flag.refresh()
            advanceUntilIdle()
            assertTrue(flag.enabled.value)
        }

    @Test
    fun `an override wins over the remote value either way`() =
        runTest {
            val override = FakeOverride()
            val flag = flag(FakeSource(cached = true), override)
            override.set(false)
            advanceUntilIdle()
            assertFalse(flag.enabled.value)
            override.set(true)
            advanceUntilIdle()
            assertTrue(flag.enabled.value)
            override.set(null)
            advanceUntilIdle()
            assertTrue(flag.enabled.value)
        }

    @Test
    fun `the override held at start applies before the store emits`() =
        runTest {
            assertTrue(flag(FakeSource(cached = false), initialOverride = true).enabled.value)
        }

    @Test
    fun `a release build has no override to read or set`() =
        runTest {
            NoDiscoveryFlagOverride.set(true)
            assertEquals(emptyList<Boolean?>(), NoDiscoveryFlagOverride.value.toList())
        }

    @Test
    fun `the data store override round trips and clears`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "flags.preferences_pb") }
            val override = DataStoreDiscoveryFlagOverride(store)
            assertNull(override.value.first())
            override.set(true)
            assertEquals(true, override.value.first())
            override.set(false)
            assertEquals(false, override.value.first())
            override.set(null)
            assertNull(override.value.first())
        }

    @Test
    fun `the override key follows the shared flags prefix`() =
        runTest {
            val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "flags2.preferences_pb") }
            DataStoreDiscoveryFlagOverride(store).set(true)
            val names =
                store.data
                    .first()
                    .asMap()
                    .keys
                    .map { it.name }
                    .toSet()
            assertEquals(setOf("override_$DISCOVERY_FLAG_KEY"), names)
        }
}
