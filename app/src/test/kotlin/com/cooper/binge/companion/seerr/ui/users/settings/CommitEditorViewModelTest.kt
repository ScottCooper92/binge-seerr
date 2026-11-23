package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.awaitEvent
import com.cooper.homelab.core.presentation.editor.DraftKeeper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** A draft kept in memory, as a page's saved state would keep it. */
private class MemoryKeeper(
    var kept: String? = null,
) : DraftKeeper<String> {
    override fun keep(draft: String?) {
        kept = draft
    }

    override fun restoreOver(loaded: String): String? = kept
}

/** An explicit-commit editor over one string, whose write the test can make fail. */
private class StringEditorViewModel(
    dispatcher: CoroutineDispatcher,
    keeper: DraftKeeper<String>? = null,
) : CommitEditorViewModel<String>(dispatcher, keeper) {
    var server = "saved"
    var failure: Throwable? = null
    val writes = mutableListOf<String>()

    override suspend fun load() = server

    override suspend fun write(draft: String): String {
        writes += draft
        failure?.let { throw it }
        server = draft
        return draft
    }

    override fun canSave(draft: String) = draft.isNotBlank()
}

/** The explicit-commit base on the shared library's form: what it adopts, what it keeps, and what it says. */
class CommitEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private fun editor(keeper: DraftKeeper<String>? = null) =
        StringEditorViewModel(mainDispatcherRule.dispatcher, keeper).also {
            viewModels.put("string", it)
            it.reload()
        }

    private fun StringEditorViewModel.ready() = uiState.value as EditorUiState.Ready<String>

    @Test
    fun `a commit adopts what the server answered as both the draft and the saved record, and says Saved`() =
        runTest {
            val vm = editor()
            vm.edit { "changed" }
            assertTrue(vm.ready().dirty)

            val saved = awaitEvent(vm.events)
            vm.save()

            assertEquals(EditorEvent.Saved, saved.await())
            assertEquals(listOf("changed"), vm.writes)
            assertEquals("changed", vm.ready().draft)
            assertEquals("changed", vm.ready().saved)
            assertFalse(vm.ready().dirty)
        }

    @Test
    fun `a draft the page may not send is not written`() =
        runTest {
            val vm = editor()
            vm.edit { " " }

            vm.save()

            assertTrue(vm.writes.isEmpty())
            assertEquals(" ", vm.ready().draft)
        }

    @Test
    fun `a commit that fails keeps the draft, unlocks the page and says why`() =
        runTest {
            val vm = editor()
            vm.failure = IOException("down")
            vm.edit { "changed" }

            val failed = awaitEvent(vm.events)
            vm.save()

            assertTrue(failed.await() is EditorEvent.Failed)
            assertEquals("changed", vm.ready().draft)
            assertEquals("saved", vm.ready().saved)
            assertFalse(vm.ready().saving)
        }

    @Test
    fun `an unsaved draft is kept as it is typed, put back over the record read again, and forgotten once saved`() =
        runTest {
            val keeper = MemoryKeeper()
            val vm = editor(keeper)
            vm.edit { "typed" }
            assertEquals("typed", keeper.kept)

            vm.reload()
            assertEquals("typed", vm.ready().draft)
            assertEquals("saved", vm.ready().saved)

            val saved = awaitEvent(vm.events)
            vm.save()
            saved.await()
            assertNull(keeper.kept)
        }
}
