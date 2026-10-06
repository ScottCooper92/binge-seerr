package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffSession
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.HandOffOpening
import io.github.scottcooper92.binge.seerr.handoff.HandOffProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
 * [cancel], the scope ending, a code left unused for [timeout] (when a new listener and code replace it), or,
 * once an address is in, the sign-in finishing or going [signInTimeout] without.
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
    private val onAddress: (String) -> Unit,
    private val progress: () -> HandOffProgress,
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

    /** One code's life. True when it lapsed unused and wants replacing; false when the hand-off is over. */
    private suspend fun listenOnce(): Boolean {
        val listening = openSession() ?: return false
        var lapsed = false
        try {
            // open() is not cancellable: a cancel() that ran meanwhile must not bring the plate back.
            currentCoroutineContext().ensureActive()
            session = listening
            onState(AddressHandOff.Listening(listening.url))
            lapsed = serveUntilDone(listening)
        } catch (_: IOException) {
            // Closing the socket is how cancel() stops a listener mid-accept; that is not a failure to show.
            currentCoroutineContext().ensureActive()
            onState(AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen))
        } finally {
            listening.close()
        }
        return lapsed
    }

    /** Serves [listening] through the address and the sign-in after it. True if no address ever came. */
    private suspend fun serveUntilDone(listening: AddressHandOffSession): Boolean =
        coroutineScope {
            val arrived = CompletableDeferred<Unit>()
            val serving =
                launch {
                    listening.serve(progress) { address ->
                        if (arrived.complete(Unit)) onState(null)
                        onAddress(address)
                    }
                }
            val lapsed = withTimeoutOrNull(timeout) { arrived.await() } == null
            if (!lapsed) followSignIn()
            serving.cancelAndJoin()
            lapsed
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
