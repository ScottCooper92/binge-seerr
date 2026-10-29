package com.cooper.binge.companion.seerr.flags

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagsTest {
    private class FakeSource(
        var cached: Flags = Flags(),
        var fetched: Flags? = null,
    ) : RemoteFlagSource {
        override fun current(): Flags = cached

        override suspend fun fetch(): Flags? = fetched
    }

    private class FakeOverrides : FlagOverrides {
        val state = MutableStateFlow<Map<Flag, Boolean>>(emptyMap())
        override val values = state

        override suspend fun set(
            flag: Flag,
            value: Boolean?,
        ) {
            state.value = if (value == null) state.value - flag else state.value + (flag to value)
        }
    }

    private fun TestScope.flags(
        source: RemoteFlagSource,
        overrides: FlagOverrides = FakeOverrides(),
    ) = RemoteFeatureFlags(source, overrides, TestScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `every flag is off with nothing behind it`() =
        runTest {
            assertEquals(Flags(), flags(NoRemoteFlagSource).flags.value)
        }

    @Test
    fun `the cached values are served before the first fetch lands`() =
        runTest {
            assertTrue(flags(FakeSource(cached = Flags(bingePromotion = true))).flags.value.bingePromotion)
        }

    @Test
    fun `a fetch replaces the values and a failed fetch keeps them`() =
        runTest {
            val source = FakeSource(fetched = Flags.AllOn)
            val flags = flags(source)
            flags.refresh()
            assertEquals(Flags.AllOn, flags.flags.value)
            source.fetched = null
            flags.refresh()
            assertEquals(Flags.AllOn, flags.flags.value)
        }

    @Test
    fun `an override wins over the remote value and clearing it gives the remote value back`() =
        runTest {
            val overrides = FakeOverrides()
            val flags = flags(FakeSource(cached = Flags(bingePromotion = true)), overrides)
            overrides.set(Flag.BingePromotion, false)
            assertFalse(flags.flags.first { !it.bingePromotion }.bingePromotion)
            overrides.set(Flag.BingePromotion, null)
            assertTrue(flags.flags.first { it.bingePromotion }.bingePromotion)
        }
}
