package io.github.scottcooper92.binge.seerr.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the last few calls to the saved server said about it. */
enum class SeerrConnectionHealth {
    /** Nothing is stored; there is no connection to check. */
    NotConnected,

    /** Server reachable and the credentials accepted. */
    Healthy,

    /** Server unreachable (offline, network, 5xx, rate-limited); retrying may recover. */
    Unreachable,

    /** Server reached but the credentials rejected; needs reconnecting. */
    Unauthorized,
}

/**
 * The write side of [SeerrConnectionHealthMonitor]: the interceptor reports each outcome here
 * without seeing the read side. [NoOp] backs the throwaway setup-probe client, whose unvalidated
 * candidate credentials must never feed the saved server's health.
 */
interface SeerrConnectionHealthReporter {
    fun reportSuccess()

    fun reportAuthFailure()

    fun reportNetworkFailure()

    object NoOp : SeerrConnectionHealthReporter {
        override fun reportSuccess() = Unit

        override fun reportAuthFailure() = Unit

        override fun reportNetworkFailure() = Unit
    }
}

/** A single transient failure can be a blip; only a run of them with no success between reads as unreachable. */
private const val UNREACHABLE_FAILURE_THRESHOLD = 2

/**
 * One source of truth for live connection health, fed by every call the cached client makes
 * through [SeerrHealthInterceptor], so a failure anywhere counts. Observe-only: nothing gates a
 * request on it. An auth rejection flips to [SeerrConnectionHealth.Unauthorized] at once; transient
 * failures flip to [SeerrConnectionHealth.Unreachable] only after [UNREACHABLE_FAILURE_THRESHOLD] in
 * a row; any success restores [SeerrConnectionHealth.Healthy] and clears the streak.
 */
class SeerrConnectionHealthMonitor : SeerrConnectionHealthReporter {
    private val lock = Any()
    private var networkFailureStreak = 0

    private val state = MutableStateFlow(SeerrConnectionHealth.NotConnected)
    val health: StateFlow<SeerrConnectionHealth> = state.asStateFlow()

    /** Reported from OkHttp threads, so the streak and the state move together under the lock. */
    override fun reportSuccess() =
        synchronized(lock) {
            networkFailureStreak = 0
            state.value = SeerrConnectionHealth.Healthy
        }

    override fun reportAuthFailure() =
        synchronized(lock) {
            networkFailureStreak = 0
            state.value = SeerrConnectionHealth.Unauthorized
        }

    override fun reportNetworkFailure() =
        synchronized(lock) {
            networkFailureStreak++
            if (networkFailureStreak >= UNREACHABLE_FAILURE_THRESHOLD) {
                state.value = SeerrConnectionHealth.Unreachable
            }
        }

    /** A connection was just validated against the server: start clean at healthy. */
    fun onConnected() =
        synchronized(lock) {
            networkFailureStreak = 0
            state.value = SeerrConnectionHealth.Healthy
        }

    /** The credentials were cleared: there is nothing to be healthy about. */
    fun reset() =
        synchronized(lock) {
            networkFailureStreak = 0
            state.value = SeerrConnectionHealth.NotConnected
        }
}
