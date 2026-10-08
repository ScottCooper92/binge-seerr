package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffSession
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.HandOffCredentials
import io.github.scottcooper92.binge.seerr.handoff.HandOffOpening
import io.github.scottcooper92.binge.seerr.handoff.HandOffProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long a hand-off code stays good when no phone uses it; a fresh one then takes its place. */
internal val HAND_OFF_TIMEOUT: Duration = 5.minutes

/** How long the page keeps following the TV once an address is in, before giving up on the sign-in. */
internal val HAND_OFF_SIGN_IN_TIMEOUT: Duration = 10.minutes

/** How long the listener outlives "Connected", so a phone's page gets to read it. */
internal val HAND_OFF_LINGER: Duration = 6.seconds

private const val PROGRESS_POLL_MILLIS = 500L

/**
 * The television's "send the address from your phone" (#323): it opens a listener, shows its code
 * through [onState], and hands the address a phone sends to [onAddress]. Every way out closes the listener:
 * [cancel], the scope ending, a code left unused for [timeout] or locked by wrong PINs (when a new listener and code
 * replace it), or, once an address is in, the sign-in finishing or going [signInTimeout] without.
 *
 * After the address the listener stays up so the phone's page can follow the TV, reading [progress] on each
 * request. A second address is passed on too, if the first named no server the TV could reach.
 *
 * Nothing here is kept in `SavedStateHandle` the way [SetupLinks] keeps a pending sign-in. A listening
 * socket does not outlive its process, so a code restored after process death would point at
 * nothing; the user starts a new one instead.
 */
internal class SetupHandOff(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val handOffs: AddressHandOffs,
    private val onState: (AddressHandOff?) -> Unit,
    private val onAddress: (address: String, session: String?) -> Unit,
    private val onCredentials: (HandOffCredentials) -> Unit,
    private val progress: () -> HandOffProgress,
    /** The code that is live right now, whichever step shows it; null once its listener has closed. */
    private val onCode: (AddressHandOff.Listening?) -> Unit = {},
    private val timeout: Duration = HAND_OFF_TIMEOUT,
    private val signInTimeout: Duration = HAND_OFF_SIGN_IN_TIMEOUT,
    private val linger: Duration = HAND_OFF_LINGER,
) {
    private var job: Job? = null

    @Volatile
    private var session: AddressHandOffSession? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(dispatcher) { listen() }
    }

    /** Stops listening now, rather than when the listener next looks up. */
    fun cancel() {
        job?.cancel()
        job = null
        session?.close()
        session = null
    }

    /**
     * A code is good for [timeout], then a fresh one (new port, new token) takes its place until an address
     * arrives or the plate goes: the page is a place to wait, not a form that times out.
     */
    private suspend fun listen() {
        var renew = true
        while (renew) renew = listenOnce()
    }

    /** One code's life. True when it wants replacing, having lapsed unused or been locked; false when the hand-off is over. */
    private suspend fun listenOnce(): Boolean {
        val listening = openSession() ?: return false
        var lapsed = false
        try {
            // open() is not cancellable: a cancel() that ran meanwhile must not bring the plate back.
            currentCoroutineContext().ensureActive()
            session = listening
            val code = AddressHandOff.Listening(listening.url, listening.scanUrl, listening.pin)
            // Started on the sign-in step there is no address plate to show: the code is that step's.
            if (progress() !is HandOffProgress.SignIn) onState(code)
            onCode(code)
            lapsed = serveUntilDone(listening)
        } catch (_: IOException) {
            // Closing the socket is how cancel() stops a listener mid-accept; that is not a failure to show.
            currentCoroutineContext().ensureActive()
            onState(AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen))
        } finally {
            listening.close()
            onCode(null)
        }
        return lapsed
    }

    /**
     * Serves [listening] through the address and the sign-in after it. True if no address ever came, or if wrong PINs
     * locked the code at any point: the listener returns then, and a new code takes its place (#916). On the sign-in
     * step that new code is the step's.
     */
    private suspend fun serveUntilDone(listening: AddressHandOffSession): Boolean =
        coroutineScope {
            val arrived = CompletableDeferred<Unit>()
            // On the sign-in step the address is already in: the code is there for a phone to finish the sign-in.
            if (progress() is HandOffProgress.SignIn) arrived.complete(Unit)
            val serving =
                launch {
                    listening.serve(
                        progress = progress,
                        onAddress = { address, session ->
                            if (arrived.complete(Unit)) onState(null)
                            onAddress(address, session)
                        },
                        onCredentials = onCredentials,
                    )
                }
            val renew =
                when (withTimeoutOrNull(timeout) { untilLocked(serving, arrived) }) {
                    null, Stage.Locked -> true
                    Stage.Done -> {
                        val following = async { followSignIn() }
                        val stage = untilLocked(serving, following)
                        following.cancel()
                        stage == Stage.Locked && awaitStepWithCode()
                    }
                }
            serving.cancelAndJoin()
            renew
        }

    /**
     * After a lock mid-sign-in: lets the TV finish reading the address it has, then says whether it stopped on a step a
     * new code is for. Connected, or a sign-in that never comes, needs none.
     */
    private suspend fun awaitStepWithCode(): Boolean =
        withTimeoutOrNull(signInTimeout) {
            while (progress().let { it is HandOffProgress.Checking || it is HandOffProgress.ConfirmOnTv }) delay(PROGRESS_POLL_MILLIS)
            progress() !is HandOffProgress.Connected
        } ?: false

    private enum class Stage { Done, Locked }

    /** Waits for [work], or for [serving] to return first because the code locked. */
    private suspend fun untilLocked(
        serving: Job,
        work: Deferred<Unit>,
    ): Stage =
        select {
            work.onAwait { Stage.Done }
            serving.onJoin { Stage.Locked }
        }

    /** Waits for the sign-in to finish, then a moment more; or gives up on it. */
    private suspend fun followSignIn() {
        withTimeoutOrNull(signInTimeout) {
            while (progress() !is HandOffProgress.Connected) delay(PROGRESS_POLL_MILLIS)
            delay(linger)
        }
    }

    /** The listener, or null after saying why there is none. */
    private fun openSession(): AddressHandOffSession? =
        try {
            when (val opening = handOffs.open()) {
                is HandOffOpening.Opened -> opening.session
                HandOffOpening.NoLocalNetwork -> null.also { onState(AddressHandOff.Unavailable(AddressHandOff.Reason.NoLocalNetwork)) }
            }
        } catch (_: IOException) {
            onState(AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen))
            null
        }
}
