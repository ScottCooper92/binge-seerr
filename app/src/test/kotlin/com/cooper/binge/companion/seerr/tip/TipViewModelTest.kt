package com.cooper.binge.companion.seerr.tip

import android.app.Activity
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

private class FakeTipBilling(
    private val prices: Map<Int, String>?,
    var outcome: CompletableDeferred<TipOutcome> = CompletableDeferred(TipOutcome.Thanks),
) : TipBilling {
    val given = mutableListOf<Int>()

    override suspend fun prices(): Map<Int, String>? = prices

    override suspend fun give(
        activity: Activity,
        step: Int,
    ): TipOutcome {
        given += step
        return outcome.await()
    }
}

private val PRICED = TIP_STEPS.associateWith { "£$it" }

@RunWith(RobolectricTestRunner::class)
class TipViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    @Test
    fun `the jar is ready once every step is priced`() {
        assertEquals(TipUiState.Ready(PRICED), TipViewModel(FakeTipBilling(PRICED)).uiState.value)
    }

    @Test
    fun `the jar is unavailable where Play cannot price it`() {
        assertEquals(TipUiState.Unavailable, TipViewModel(FakeTipBilling(null)).uiState.value)
    }

    @Test
    fun `a bought tip says thanks`() =
        runTest {
            val billing = FakeTipBilling(PRICED)
            val viewModel = TipViewModel(billing)
            val event = awaitEvent(viewModel.events)
            viewModel.give(activity, 5)
            assertEquals(TipEvent.Thanks, event.await())
            assertEquals(listOf(5), billing.given)
        }

    @Test
    fun `a tip waiting on Play and a failed one each say so`() =
        runTest {
            val pending = FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Pending))
            val viewModel = TipViewModel(pending)
            val first = awaitEvent(viewModel.events)
            viewModel.give(activity, 2)
            assertEquals(TipEvent.Pending, first.await())

            val failed = FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Failed))
            val other = TipViewModel(failed)
            val second = awaitEvent(other.events)
            other.give(activity, 2)
            assertEquals(TipEvent.Failed, second.await())
        }

    @Test
    fun `a cancelled tip says nothing and can be tried again`() =
        runTest {
            val billing = FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Cancelled))
            val viewModel = TipViewModel(billing)
            viewModel.give(activity, 5)
            billing.outcome = CompletableDeferred(TipOutcome.Thanks)
            val event = awaitEvent(viewModel.events)
            viewModel.give(activity, 5)
            assertEquals(TipEvent.Thanks, event.await())
            assertEquals(listOf(5, 5), billing.given)
        }

    @Test
    fun `a second tap while a purchase is under way does not start another`() =
        runTest {
            val billing = FakeTipBilling(PRICED, CompletableDeferred())
            val viewModel = TipViewModel(billing)
            viewModel.give(activity, 5)
            viewModel.give(activity, 9)
            assertEquals(listOf(5), billing.given)
            billing.outcome.complete(TipOutcome.Thanks)
        }

    @Test
    fun `nothing is given while the jar is unavailable`() {
        val billing = FakeTipBilling(null)
        TipViewModel(billing).give(activity, 5)
        assertTrue(billing.given.isEmpty())
    }
}
