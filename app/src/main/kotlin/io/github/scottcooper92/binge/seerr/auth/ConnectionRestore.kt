package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Brings the connection back on a device that has none, from whatever a transfer or a cloud restore
 * left in the [ConnectionCarrier].
 *
 * Carried credentials are never saved on the strength of being present: the server is asked who they
 * belong to first. One that rejects them empties the carrier; one that merely cannot be reached
 * leaves it be, because a flat battery today is not a reason to lose the connection for good.
 */
class ConnectionRestore(
    private val store: CredentialStore,
    private val apis: SeerrApiFactory,
    private val carrier: ConnectionCarrier,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(false)

    /** False until a restore has been tried, so a screen can hold "not connected" back until it has. */
    val settled: StateFlow<Boolean> = state.asStateFlow()

    /** Fired once from the application, so the first screen has an answer to wait for. */
    fun start() {
        scope.launch { run() }
    }

    /** Runs at most once per process; a second call returns without touching the carrier. */
    suspend fun run() {
        if (state.value) return
        try {
            if (store.credentials.first() != null) return
            val carried = carrier.read() ?: return
            attempt { apis.probe(carried.baseUrl, carried.auth) { api -> api.authenticatedUser() } }
                .onSuccess { store.save(carried) }
                .onFailure { failure -> if (failure.toSeerrError().isRefusal) carrier.clear() }
        } finally {
            state.value = true
        }
    }
}

/** The server understood the credentials and would not have them, as opposed to not answering. */
private val SeerrError.isRefusal: Boolean
    get() = this == SeerrError.Unauthorized || this == SeerrError.Forbidden
