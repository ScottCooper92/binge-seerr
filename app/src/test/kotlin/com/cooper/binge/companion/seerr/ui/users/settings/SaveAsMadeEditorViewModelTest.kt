package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import com.binge.designsystem.save.SAVE_AS_MADE_DELAY_MILLIS
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.homelab.core.server.network.LocalNetworkDeniedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * What the base presents from the shared library's engine, on a page of one number. A refusal and a write that cannot reach the
 * server are covered on real pages, such as `NetworkViewModelTest`.
 */
class SaveAsMadeEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private class Page(
        scope: CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        private val onLoad: suspend () -> Int,
        private val onWrite: suspend (Int) -> Int,
    ) : SaveAsMadeEditorViewModel<Int>(dispatcher, scope) {
        override suspend fun load(): Int = onLoad()

        override suspend fun write(draft: Int): Int = onWrite(draft)
    }

    private fun TestScope.page(
        onLoad: suspend () -> Int = { 1 },
        onWrite: suspend (Int) -> Int = { it },
    ): Page {
        val page = Page(backgroundScope, mainDispatcherRule.dispatcher, onLoad, onWrite)
        viewModels.put(page.hashCode().toString(), page)
        page.reload()
        return page
    }

    private suspend fun Page.awaitReady() = uiState.first { it is EditorUiState.Ready } as EditorUiState.Ready<Int>

    @Test
    fun `a load that fails is the error the page reads`() =
        runTest {
            val page = page(onLoad = { throw IOException("down") })
            advanceUntilIdle()

            val state = page.uiState.value as EditorUiState.Error
            assertEquals(SeerrError.Unreachable, state.error)
        }

    @Test
    fun `a write the local network permission blocked is kept unsent with a retry`() =
        runTest {
            var blocked = true
            val page = page(onWrite = { if (blocked) throw LocalNetworkDeniedException("seerr.lan") else it })
            page.awaitReady()

            page.edit { 2 }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            advanceUntilIdle()
            val failed = page.awaitReady()
            assertTrue(failed.saveFailed)
            assertEquals(2, failed.draft)
            assertEquals(null, failed.saveRefusal)

            blocked = false
            page.save()
            advanceUntilIdle()
            val saved = page.awaitReady()
            assertFalse(saved.saveFailed)
            assertEquals(2, saved.saved)
        }
}
