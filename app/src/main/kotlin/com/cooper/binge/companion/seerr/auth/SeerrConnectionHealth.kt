package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.isArrProxyRoute
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.server.connection.CallResult
import com.cooper.homelab.core.server.connection.ConnectionHealth
import com.cooper.homelab.core.server.connection.ConnectionHealthMonitor
import com.cooper.homelab.core.server.connection.SeerrClassifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

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
 * The write side of [SeerrHealth]: the interceptor reports each call here without seeing the read side. [NoOp] backs the
 * throwaway setup-probe client, whose unvalidated candidate credentials must never feed the saved server's health.
 */
fun interface SeerrHealthReporter {
    fun report(result: CallResult)

    object NoOp : SeerrHealthReporter {
        override fun report(result: CallResult) = Unit
    }
}

/** A single transient failure can be a blip; only a run of them with no answer between reads as unreachable. */
private const val UNREACHABLE_AFTER = 2

/** The shared library's monitor with this app's rules: [SeerrClassifier], and two calls in a row with no answer is unreachable. */
fun seerrHealthMonitor(): ConnectionHealthMonitor = ConnectionHealthMonitor(SeerrClassifier(::isArrProxyRoute), UNREACHABLE_AFTER)

/**
 * Live health of the one saved server, fed by every call the cached client makes. Observe-only. What a call means is the shared
 * library's rule ([SeerrClassifier]); this keys it by [server], which is minted once and persisted, so [report] (on an OkHttp
 * thread, which blocks anyway) reads it with `runBlocking` once and keeps it: a DataStore read, warm after the first.
 * It does not read the store: "not connected" is [SeerrConnection]'s call.
 */
class SeerrHealth(
    private val monitor: ConnectionHealthMonitor,
    private val server: suspend () -> ServerId,
) : SeerrHealthReporter {
    private val id: ServerId by lazy { runBlocking { server() } }

    val health: Flow<ConnectionHealth> = flow { emitAll(monitor.health(server())) }

    override fun report(result: CallResult) = monitor.report(id, result)

    /** A connection was just validated against the server: start clean at healthy. */
    suspend fun onConnected() = monitor.onConnected(server())

    /** The credentials were cleared: whatever the last call said no longer describes anything. */
    suspend fun reset() = monitor.reset(server())

    companion object {
        /** Health for a connection that has no saved identity to key it by, such as a test's: one server, named here. */
        fun standalone(): SeerrHealth = SeerrHealth(seerrHealthMonitor()) { ServerId("saved") }
    }
}

/** The app's enum for the shared library's verdict; [SeerrConnectionHealth.NotConnected] is the store's to say, not the monitor's. */
internal fun ConnectionHealth.toSeerr(): SeerrConnectionHealth =
    when (this) {
        ConnectionHealth.Unchecked -> SeerrConnectionHealth.Unchecked
        ConnectionHealth.Healthy -> SeerrConnectionHealth.Healthy
        ConnectionHealth.Unreachable -> SeerrConnectionHealth.Unreachable
        ConnectionHealth.Unauthorized -> SeerrConnectionHealth.Unauthorized
    }
