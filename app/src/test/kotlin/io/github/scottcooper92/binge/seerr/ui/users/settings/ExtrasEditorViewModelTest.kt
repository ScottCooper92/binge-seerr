package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A minimal [ExtrasEditorViewModel] whose extras are a plain counter, for driving [editExtras] directly. */
private class CounterEditorViewModel(
    dispatcher: CoroutineDispatcher,
) : ExtrasEditorViewModel<Unit, Int>(initialExtras = 0, dispatcher = dispatcher) {
    override suspend fun load() = Unit

    override suspend fun write(draft: Unit) = draft

    fun increment() = editExtras { it + 1 }

    fun count() = currentExtras()
}

private const val THREAD_COUNT = 32
private const val INCREMENTS_PER_THREAD = 200
private const val EXPECTED_TOTAL = THREAD_COUNT * INCREMENTS_PER_THREAD

/**
 * #387: `editExtras` used to read-modify-write a plain `var`, so two calls landing on different
 * `Dispatchers.IO` threads at the same moment could both read the same starting value and the
 * later write would silently discard the earlier one. Coroutines alone can't reproduce that —
 * `runTest`'s scheduler is cooperative and never runs two bodies at once — so this drives
 * [ExtrasEditorViewModel.editExtras] from a real thread pool instead, at enough concurrency that
 * the old `var`-based implementation would reliably lose some of the total.
 */
class ExtrasEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()

    private lateinit var vm: CounterEditorViewModel

    @Before
    fun setUp() {
        // The unconfined test dispatcher makes reload()'s load() (which never suspends) run to
        // completion synchronously, so the view model is Ready before reload() returns.
        vm = CounterEditorViewModel(mainDispatcherRule.dispatcher)
        viewModels.put("counter", vm)
        vm.reload()
    }

    @After
    fun tearDown() = viewModels.clear()

    @Test
    fun `concurrent editExtras calls from independent threads all land`() {
        val pool = Executors.newFixedThreadPool(THREAD_COUNT)
        val allStarted = CountDownLatch(THREAD_COUNT)
        val start = CountDownLatch(1)
        try {
            repeat(THREAD_COUNT) {
                pool.submit {
                    allStarted.countDown()
                    start.await()
                    repeat(INCREMENTS_PER_THREAD) { vm.increment() }
                }
            }
            assertTrue(allStarted.await(5, TimeUnit.SECONDS))
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }

        // The internal accumulator every editExtras call folds into...
        assertEquals(EXPECTED_TOTAL, vm.count())
        // ...and the copy published on uiState, which is what a screen actually renders, must agree.
        val ready = vm.uiState.value as ExtrasEditorUiState.Ready<Unit, Int>
        assertEquals(EXPECTED_TOTAL, ready.extras)
    }
}
