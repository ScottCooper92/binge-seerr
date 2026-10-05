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

/** How long a hand-off code stays good when no phone uses it. */
internal val HAND_OFF_TIMEOUT: Duration = 5.minutes

/**
 * The television's "send the address from your phone" (#323): it opens a listener, shows its code
 * through [onState], and hands the one address a phone sends to [onAddress]. The listener is closed
 * on every way out — an address, [HAND_OFF_TIMEOUT] ([onExpired]), [cancel], or the scope ending.
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
    private val onExpired: () -> Unit,
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

    private suspend fun listen() {
        val opening =
            try {
                handOffs.open()
            } catch (_: IOException) {
                return onState(AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen))
            }
        val opened =
            opening as? HandOffOpening.Opened
                ?: return onState(AddressHandOff.Unavailable(AddressHandOff.Reason.NoLocalNetwork))
        val listening = opened.session
        try {
            // open() is not cancellable: a cancel() that ran meanwhile must not bring the plate back.
            currentCoroutineContext().ensureActive()
            session = listening
            onState(AddressHandOff.Listening(listening.url))
            val address = withTimeoutOrNull(timeout) { listening.awaitAddress() }
            onState(null)
            if (address == null) onExpired() else onAddress(address)
        } catch (_: IOException) {
            // Closing the socket is how cancel() stops a listener mid-accept; that is not a failure to show.
            currentCoroutineContext().ensureActive()
            onState(AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen))
        } finally {
            listening.close()
        }
    }
}
