package com.cooper.binge.companion.seerr.ui.users.settings

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.attempt
import com.cooper.binge.companion.seerr.seerr.isTransient
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
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
 * after it, from what the server answered. A write that fails [transiently][isTransient] keeps the change, unsent, and says
 * so through [failed], which stays true until a write succeeds (a later change does not clear it, only its success does);
 * [now] sends it again at once. A write the server refuses would only be refused again, so it is not kept: [refused]
 * takes the reason, once (#1475). [status] follows the writes for the inline "Saving… / Saved" line.
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
    private val status: (SaveStatus) -> Unit = {},
    /** A write the server refused: nothing is owed, and the page puts its saved record back and says why. */
    private val refused: suspend (SeerrError) -> Unit = {},
) {
    // All of this is touched on [scope]'s thread only (the main thread for a ViewModel); just the write itself leaves it.
    private var pending: Job? = null
    private var inFlight: Deferred<Result<T>>? = null
    private var unsent = false

    // The latest write, cleared only by [discard]: cancelling [scope] ends send()'s wait on it, but the write itself is still running.
    private var lastWrite: Deferred<Result<T>>? = null

    /** The draft changed: write it once the changes stop. */
    fun changed() = schedule(SAVE_AS_MADE_DELAY_MILLIS)

    /** Write the draft now: a retry after a failure. */
    fun now() = schedule(0)

    private fun schedule(wait: Long) {
        unsent = true
        // A write in flight is never cancelled or overlapped: its answer is adopted, and what changed meanwhile goes next.
        if (inFlight != null) return
        pending?.cancel()
        pending =
            scope.launch {
                delay(wait)
                send()
            }
    }

    private suspend fun send() {
        // Nothing it may send: the last change is held, not saved, so the line must not keep saying Saving.
        val sent = draft()?.takeIf(canSave) ?: return status(SaveStatus.Idle)
        unsent = false
        status(SaveStatus.Saving)
        // Caught inside the write, so a failure stays this save's and does not reach the application's scope.
        val write = appScope.async(dispatcher) { attempt { write(sent) } }
        inFlight = write
        lastWrite = write
        val result =
            try {
                write.await()
            } finally {
                inFlight = null
            }
        result
            .onSuccess { adopted ->
                failed(false)
                // More to send: the line keeps saying Saving until the last write lands.
                if (adopt(sent, adopted) || unsent) changed() else status(SaveStatus.Saved)
            }.onFailure { failure ->
                val error = failure.toSeerrError()
                status(SaveStatus.Idle)
                if (error.isTransient) {
                    unsent = true
                    failed(true)
                } else {
                    // Nothing owed: a later change rode on the refused record, and settle() must not send it again.
                    unsent = false
                    lastWrite = null
                    failed(false)
                    refused(error)
                }
            }
    }

    /**
     * The page is about to read its record again (#958): a change still waiting, or one whose write failed, is sent first,
     * after any write already running, so the read sees it. Called before the page drops its draft, which is captured
     * here. Answers the draft if its write failed, so the page can keep it on top of what it reads, and null otherwise.
     */
    fun settle(): Deferred<T?> {
        pending?.cancel()
        val owed = unsent
        // Owed here now, so the running write's answer does not schedule another send of the draft about to be replaced.
        unsent = false
        val kept = draft()
        val earlier = lastWrite
        return appScope.async(dispatcher) {
            val earlierError = earlier?.await()?.exceptionOrNull()?.toSeerrError()
            if (earlierError?.isTransient == false) {
                // The write send() was waiting on, which this settle cancelled: its refusal is reported here, and nothing rides on it.
                report(earlierError)
                return@async null
            }
            val left = kept?.takeIf { (owed || earlierError != null) && canSave(it) } ?: return@async null
            val error = attempt { write(left) }.exceptionOrNull()?.toSeerrError() ?: return@async null
            if (error.isTransient) {
                left
            } else {
                report(error)
                null
            }
        }
    }

    /** A refusal [settle] met, told on [scope] because [refused] touches the page's state, which only that thread does. */
    private fun report(error: SeerrError) {
        scope.launch { refused(error) }
    }

    /**
     * The record on the server was replaced from elsewhere, as the sliders' Reset replaces the list (#1019): a change still
     * waiting, or one whose write failed, is dropped rather than written over it. A write already running is left to finish,
     * but its answer is not taken: [pending] is the send waiting on it, so cancelling it drops the answer, and nothing it
     * would have adopted or queued again reaches the page (#1460).
     */
    fun discard() {
        pending?.cancel()
        unsent = false
        lastWrite = null
        status(SaveStatus.Idle)
        failed(false)
    }

    /** The page has read its record again; [keptUnsent] says whether it kept a change [settle] could not send. */
    fun reloaded(keptUnsent: Boolean) {
        scope.launch { unsent = keptUnsent }
    }

    /**
     * The page is going: a change still waiting is sent now, where the page's own scope can no longer cancel it, after
     * any write already running. That includes a change whose write failed, so leaving with the failure showing retries
     * it once, and a second failure is not reported.
     */
    fun cleared() {
        val left = draft()?.takeIf(canSave) ?: return
        if (!unsent) return
        pending?.cancel()
        val earlier = lastWrite
        appScope.launch(dispatcher) {
            earlier?.await()
            attempt { write(left) }
        }
    }
}
