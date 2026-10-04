package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.attempt
import io.github.scottcooper92.binge.seerr.seerr.insecurePublicHostOrNull
import io.github.scottcooper92.binge.seerr.seerr.rejectsSession
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
 *
 * On a device that is already connected, it brings the carrier up to date instead: see [refreshCarrier].
 */
class ConnectionRestore(
    private val store: CredentialStore,
    private val apis: SeerrApiFactory,
    private val carrier: ConnectionCarrier,
    private val scope: CoroutineScope,
    /** The same consent [apis] enforces: a carried opt-in is granted here so the probe may go out. */
    private val cleartext: CleartextConsent = CleartextConsent.None,
) {
    private val state = MutableStateFlow(false)

    /** False until a restore has been tried, so a screen can hold "not connected" back until it has. */
    val settled: StateFlow<Boolean> = state.asStateFlow()

    /** Fired once from the application, so the first screen has an answer to wait for. */
    fun start() {
        scope.launch {
            run()
            refreshCarrier()
        }
    }

    /** Runs at most once per process; a second call returns without touching the carrier. */
    suspend fun run() {
        if (state.value) return
        try {
            if (store.credentials.first() != null) return
            val (carried, optedIn) = carrier.read() ?: return
            // The opt-in travelled with the connection, so it is granted before the probe and dropped
            // again unless the connection is saved: consent belongs to the saved server only.
            val host = carried.baseUrl.insecurePublicHostOrNull()?.takeIf { optedIn }
            host?.let { cleartext.grant(it) }
            val proved = attempt { apis.probe(carried.baseUrl, carried.auth) { api -> api.authenticatedUser() } }
            val saved = proved.isSuccess && store.save(carried)
            if (host != null && !saved) cleartext.retainOnly(null)
            proved.onFailure { failure -> if (failure.toSeerrError().rejectsSession) carrier.clear() }
        } finally {
            state.value = true
        }
    }

    /**
     * Rewrites the carrier when it does not hold what a save of the saved connection would put there.
     *
     * Only a save writes the carrier, so a connection saved before the carrier held the plain-HTTP opt-in
     * is carried without it (#721). So is one whose consent was grandfathered in by
     * [DataStoreCleartextConsent], whose user never saw the opt-in. A new device would refuse to restore
     * either, so this runs on every start. It reads the carrier first, so a carrier already in step is not
     * written again. The saved connection is read again before the write, so a connect that lands
     * meanwhile is not overwritten with the one it replaced.
     */
    suspend fun refreshCarrier() {
        val saved = store.credentials.first() ?: return
        val expected = cleartext.carriedFor(saved)
        if (carrier.read() == expected) return
        if (store.credentials.first() == saved) carrier.put(expected)
    }
}
