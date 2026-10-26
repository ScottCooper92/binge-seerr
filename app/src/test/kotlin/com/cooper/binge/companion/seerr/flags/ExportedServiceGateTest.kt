package com.cooper.binge.companion.seerr.flags

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExportedServiceGateTest {
    private class MutableFlags(
        initial: Flags,
    ) : FeatureFlags {
        override val flags: MutableStateFlow<Flags> = MutableStateFlow(initial)

        override suspend fun refresh() = Unit
    }

    private val calls = mutableListOf<Boolean>()

    private fun TestScope.gate(flags: FeatureFlags) =
        ExportedServiceGate(flags, { calls += it }, TestScope(UnconfinedTestDispatcher(testScheduler))).also { it.start() }

    @Test
    fun `the Service is hidden at start while the flag is off`() =
        runTest {
            gate(MutableFlags(Flags()))

            assertEquals(listOf(false), calls)
        }

    @Test
    fun `the Service comes back when the flag turns on and goes again when it turns off`() =
        runTest {
            val flags = MutableFlags(Flags())
            gate(flags)

            flags.flags.value = Flags(requestCompanions = true)
            flags.flags.value = Flags(requestCompanions = false)

            assertEquals(listOf(false, true, false), calls)
        }

    @Test
    fun `a change to the other flag does not touch the component`() =
        runTest {
            val flags = MutableFlags(Flags.AllOn)
            gate(flags)

            flags.flags.value = Flags(bingePromotion = false, requestCompanions = true)

            assertEquals(listOf(true), calls)
        }
}
