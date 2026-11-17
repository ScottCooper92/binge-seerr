package com.cooper.binge.companion.seerr.flags

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTogglesFlagTest {
    private class FakeOverride(
        initial: Boolean?,
    ) : SearchTogglesOverride {
        val state = MutableStateFlow(initial)
        override val value = state

        override suspend fun set(value: Boolean?) {
            state.value = value
        }
    }

    @Test
    fun `the flag is off with no override`() =
        runTest(UnconfinedTestDispatcher()) {
            assertFalse(LocalSearchTogglesFlag(FakeOverride(null), backgroundScope).enabled.value)
        }

    @Test
    fun `a release build has no override and the flag stays off`() =
        runTest(UnconfinedTestDispatcher()) {
            assertFalse(LocalSearchTogglesFlag(NoSearchTogglesOverride, backgroundScope).enabled.value)
        }

    @Test
    fun `an override of on turns it on, and clearing it turns it off`() =
        runTest(UnconfinedTestDispatcher()) {
            val override = FakeOverride(true)
            val flag = LocalSearchTogglesFlag(override, backgroundScope, initialOverride = true)
            assertTrue(flag.enabled.value)

            override.set(null)

            assertFalse(flag.enabled.value)
        }
}
