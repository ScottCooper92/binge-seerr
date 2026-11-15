package com.cooper.binge.companion.seerr.tip

import android.app.Activity
import com.cooper.binge.companion.seerr.flags.FixedFeatureFlags
import com.cooper.binge.companion.seerr.flags.Flags
import com.cooper.binge.companion.seerr.review.ReviewPrompt
import com.cooper.binge.companion.seerr.review.ReviewSignalStore
import com.cooper.binge.companion.seerr.util.InMemoryDataStore
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days

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

private const val NOW = 1_800_000_000_000L

private val PRICED = TIP_STEPS.associateWith { "£$it" }

@RunWith(RobolectricTestRunner::class)
class TipViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()
    private val store = ReviewSignalStore(InMemoryDataStore())
    private val asked = mutableListOf<Activity>()
    private val reviewPrompt =
        ReviewPrompt(store, { asked += it }, FixedFeatureFlags(Flags(reviewPrompt = true)), TestScope(UnconfinedTestDispatcher()))
            .apply { clock = { NOW } }

    private fun viewModel(billing: TipBilling) = TipViewModel(billing, reviewPrompt)

    /** A user who has earned the prompt, so only the tip can be what stops it. */
    private suspend fun earn() {
        store.stampFirstLaunchIfUnset(NOW - 20.days.inWholeMilliseconds)
        repeat(5) { store.incrementPositiveActionCount() }
    }

    @Test
    fun `the jar is ready once every step is priced`() {
        assertEquals(TipUiState.Ready(PRICED), viewModel(FakeTipBilling(PRICED)).uiState.value)
    }

    @Test
    fun `the jar is unavailable where Play cannot price it`() {
        assertEquals(TipUiState.Unavailable, viewModel(FakeTipBilling(null)).uiState.value)
    }

    @Test
    fun `a bought tip says thanks`() =
        runTest {
            val billing = FakeTipBilling(PRICED)
            val viewModel = viewModel(billing)
            val event = awaitEvent(viewModel.events)
            viewModel.give(activity, 5)
            assertEquals(TipEvent.Thanks, event.await())
            assertEquals(listOf(5), billing.given)
        }

    @Test
    fun `a tip waiting on Play and a failed one each say so`() =
        runTest {
            val pending = FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Pending))
            val viewModel = viewModel(pending)
            val first = awaitEvent(viewModel.events)
            viewModel.give(activity, 2)
            assertEquals(TipEvent.Pending, first.await())

            val failed = FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Failed))
            val other = viewModel(failed)
            val second = awaitEvent(other.events)
            other.give(activity, 2)
            assertEquals(TipEvent.Failed, second.await())
        }

    @Test
    fun `a cancelled tip says nothing and can be tried again`() =
        runTest {
            val billing = FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Cancelled))
            val viewModel = viewModel(billing)
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
            val viewModel = viewModel(billing)
            viewModel.give(activity, 5)
            viewModel.give(activity, 9)
            assertEquals(listOf(5), billing.given)
            billing.outcome.complete(TipOutcome.Thanks)
        }

    @Test
    fun `nothing is given while the jar is unavailable`() {
        val billing = FakeTipBilling(null)
        viewModel(billing).give(activity, 5)
        assertTrue(billing.given.isEmpty())
    }

    @Test
    fun `a given tip ends the review prompt for the session`() =
        runTest {
            earn()
            val viewModel = viewModel(FakeTipBilling(PRICED))
            val event = awaitEvent(viewModel.events)
            viewModel.give(activity, 5)
            assertEquals(TipEvent.Thanks, event.await())
            reviewPrompt.maybeAsk(activity)
            assertEquals(emptyList<Activity>(), asked)
        }

    @Test
    fun `a tip still pending also ends it`() =
        runTest {
            earn()
            val viewModel = viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Pending)))
            val event = awaitEvent(viewModel.events)
            viewModel.give(activity, 5)
            assertEquals(TipEvent.Pending, event.await())
            reviewPrompt.maybeAsk(activity)
            assertEquals(emptyList<Activity>(), asked)
        }

    @Test
    fun `a cancelled or failed tip leaves the prompt to be asked`() =
        runTest {
            earn()
            val cancelled = viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Cancelled)))
            cancelled.give(activity, 5)
            val failed = viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Failed)))
            val event = awaitEvent(failed.events)
            failed.give(activity, 5)
            assertEquals(TipEvent.Failed, event.await())
            reviewPrompt.maybeAsk(activity)
            assertEquals(listOf(activity), asked)
        }
}
