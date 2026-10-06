package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffSession
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.HandOffOpening
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How long a hand-off code stays good when no phone uses it; a fresh one then takes its place. */
internal val HAND_OFF_TIMEOUT: Duration = 5.minutes

/**
 * The television's "send the address from your phone" (#323): it opens a listener, shows its code
 * through [onState], and hands the one address a phone sends to [onAddress]. The listener is closed
 * on every way out — an address, [cancel], or the scope ending; every [HAND_OFF_TIMEOUT] without one, it is replaced by a new
 * listener and code.
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
    private val timeout: Duration = HAND_OFF_TIMEOUT,
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
            val address = withTimeoutOrNull(timeout) { listening.awaitAddress() }
            lapsed = address == null
            if (address != null) {
                onState(null)
                onAddress(address)
            }
        } catch (_: IOException) {
            // Closing the socket is how cancel() stops a listener mid-accept; that is not a failure to show.
            currentCoroutineContext().ensureActive()
            onState(AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen))
        } finally {
            listening.close()
        }
        return lapsed
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
