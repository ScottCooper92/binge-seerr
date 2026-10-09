package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long an editor that saves as it changes waits after the last change, so a burst of changes is one write. */
internal const val SAVE_AS_MADE_DELAY_MILLIS = 600L

/**
 * The save-as-made mode both editor bases share (#930): each change writes the draft a moment after the last one, with no
 * Save to press. The write runs on [appScope], so leaving the page cannot cut it off half-sent, and a change still
 * waiting out its delay when the page goes is sent then ([cleared]). A change made while a write is in flight goes out
 * after it, from what the server answered. A write that fails keeps the change, unsent, and says so through [failed]
 * until the next one; [now] sends it again at once.
 */
internal class SaveAsMade<T>(
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val draft: () -> T?,
    private val canSave: (T) -> Boolean,
    private val write: suspend (T) -> T,
    /** Takes the server's answer to [sent]; true if the draft has moved on since, so it needs writing too. */
    private val adopt: (sent: T, adopted: T) -> Boolean,
    private val failed: (Boolean) -> Unit,
) {
    private var pending: Job? = null
    private var unsent = false

    /** The draft changed: write it once the changes stop. */
    fun changed() = schedule(SAVE_AS_MADE_DELAY_MILLIS)

    /** Write the draft now: a retry after a failure. */
    fun now() = schedule(0)

    private fun schedule(wait: Long) {
        unsent = true
        pending?.cancel()
        pending =
            scope.launch(dispatcher) {
                delay(wait)
                val sent = draft()?.takeIf(canSave) ?: return@launch
                unsent = false
                failed(false)
                // Caught inside the write, so a failure stays this save's and does not reach the application's scope.
                appScope
                    .async(dispatcher) { attempt { write(sent) } }
                    .await()
                    .onSuccess { adopted -> if (adopt(sent, adopted)) changed() }
                    .onFailure {
                        unsent = true
                        failed(true)
                    }
            }
    }

    /** The page is going: a change still waiting is sent now, where the page's own scope can no longer cancel it. */
    fun cleared() {
        val left = draft()?.takeIf(canSave) ?: return
        if (!unsent) return
        pending?.cancel()
        appScope.launch(dispatcher) { attempt { write(left) } }
    }
}
