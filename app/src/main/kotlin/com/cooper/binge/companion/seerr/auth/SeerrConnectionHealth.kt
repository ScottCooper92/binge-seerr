package com.cooper.binge.companion.seerr.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the last few calls to the saved server said about it. */
enum class SeerrConnectionHealth {
    /** Nothing is stored; there is no connection to check. */
    NotConnected,

    /**
     * A connection is stored but nothing has spoken to the server yet, which is where a cold start
     * begins. Distinct from [NotConnected]: there is something to check, and no verdict on it.
     */
    Unchecked,

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
 * Live connection health, fed by every call the cached client makes through [SeerrHealthInterceptor].
 * Observe-only: nothing gates a request on it. An auth rejection flips to [SeerrConnectionHealth.Unauthorized]
 * at once, [UNREACHABLE_FAILURE_THRESHOLD] transient failures in a row to [SeerrConnectionHealth.Unreachable],
 * and any success back to [SeerrConnectionHealth.Healthy].
 *
 * It starts [SeerrConnectionHealth.Unchecked]: it does not read the store, so "not connected" is [SeerrConnection]'s call.
 */
class SeerrConnectionHealthMonitor : SeerrConnectionHealthReporter {
    private val lock = Any()
    private var networkFailureStreak = 0

    private val state = MutableStateFlow(SeerrConnectionHealth.Unchecked)
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

    /** The credentials were cleared: whatever the last call said no longer describes anything. */
    fun reset() =
        synchronized(lock) {
            networkFailureStreak = 0
            state.value = SeerrConnectionHealth.Unchecked
        }
}
