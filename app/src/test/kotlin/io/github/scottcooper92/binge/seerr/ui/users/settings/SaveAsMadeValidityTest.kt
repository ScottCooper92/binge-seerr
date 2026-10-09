package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private data class Account(
    val email: String,
    val quota: Int,
)

/** A save-as-made page whose record needs an email, as a user's General does. */
private class AccountEditorViewModel(
    private val loaded: Account,
    dispatcher: CoroutineDispatcher,
    override val saveAsMadeScope: CoroutineScope,
) : EditorViewModel<Account>(dispatcher) {
    val written = mutableListOf<Account>()

    override suspend fun load() = loaded

    override suspend fun write(draft: Account) = draft.also { written += it }

    override fun canSave(draft: Account) = draft.email.isNotBlank()
}

/** #957: a page that saves as it changes is not held back by a value the server itself sent invalid. */
class SaveAsMadeValidityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private fun CoroutineScope.editor(loaded: Account) =
        AccountEditorViewModel(loaded, mainDispatcherRule.dispatcher, saveAsMadeScope = this).also {
            viewModels.put("account", it)
            it.reload()
        }

    @Test
    fun `a change saves around a required value the server sent blank, which goes back as it came`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = editor(Account(email = "", quota = 5))

            vm.edit { it.copy(quota = 10) }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()

            assertEquals(listOf(Account(email = "", quota = 10)), vm.written)
        }

    @Test
    fun `a change that blanks a required value the server had is still held`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = editor(Account(email = "ann@example.com", quota = 5))

            vm.edit { it.copy(email = "") }
            advanceTimeBy(SAVE_AS_MADE_DELAY_MILLIS + 1)
            runCurrent()

            assertEquals(emptyList<Account>(), vm.written)
        }
}
