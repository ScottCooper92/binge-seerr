package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A save-as-made editor over one number on a fake server, whose writes the test answers one at a time. */
private class NumberEditorViewModel(
    dispatcher: CoroutineDispatcher,
    override val saveAsMadeScope: CoroutineScope,
) : EditorViewModel<Int>(dispatcher) {
    var server = 0
    val writes = mutableListOf<CompletableDeferred<Unit>>()

    override suspend fun load() = server

    override suspend fun write(draft: Int): Int {
        CompletableDeferred<Unit>().also { writes += it }.await()
        server = draft
        return draft
    }
}

/** #958: reading the record again does not race a save-as-made write that is waiting, running or failed. */
class EditorReloadTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private fun CoroutineScope.editor() =
        NumberEditorViewModel(mainDispatcherRule.dispatcher, saveAsMadeScope = this).also {
            viewModels.put("number", it)
            it.reload()
        }

    private fun NumberEditorViewModel.ready() = uiState.value as EditorUiState.Ready<Int>

    @Test
    fun `a reload during the write's delay sends the change first, then reads it back`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = editor()
            vm.edit { 5 }

            vm.reload()
            runCurrent()
            vm.writes.single().complete(Unit)
            runCurrent()

            assertEquals(EditorUiState.Ready(draft = 5, saved = 5), vm.uiState.value)
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS * 3)
            assertEquals("the cancelled delay sends nothing more", 1, vm.writes.size)
        }

    @Test
    fun `a reload during the write reads after it lands, not alongside it`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = editor()
            vm.edit { 5 }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            assertEquals(1, vm.writes.size)

            vm.reload()
            assertEquals(EditorUiState.Loading, vm.uiState.value)
            vm.writes.single().complete(Unit)
            runCurrent()

            assertEquals(EditorUiState.Ready(draft = 5, saved = 5), vm.uiState.value)
        }

    @Test
    fun `a change that cannot be sent is kept over what the reload reads, with the failure showing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = editor()
            vm.edit { 5 }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            vm.writes[0].completeExceptionally(IllegalStateException("offline"))
            runCurrent()
            assertEquals(true, vm.ready().saveFailed)

            vm.reload()
            runCurrent()
            vm.writes[1].completeExceptionally(IllegalStateException("still offline"))
            runCurrent()

            assertEquals(EditorUiState.Ready(draft = 5, saved = 0, saveFailed = true), vm.uiState.value)
            // Retry still sends the kept change.
            vm.save()
            runCurrent()
            vm.writes[2].complete(Unit)
            runCurrent()
            assertEquals(EditorUiState.Ready(draft = 5, saved = 5), vm.uiState.value)
        }
}
