package com.cooper.binge.companion.seerr.ui.hub

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val INITIAL_RETRY_DELAY_MS = 2_000L
private const val MAX_RETRY_DELAY_MS = 30_000L

/** Bounded so a server that is really down is not re-probed forever; the cold-start blip this heals clears within an attempt or two. */
private const val MAX_AUTO_RETRIES = 5

/**
 * Heals a transient failure in place. While the hub is on screen and its health is [ConnectionHealth.Unreachable],
 * [ConnectionHealth.LocalNetworkDenied], [ConnectionHealth.CouldNotLoad] or [ConnectionHealth.Unauthorized], it
 * re-probes on a bounded exponential backoff. `collectLatest` restarts on every change, so leaving that set or the
 * screen cancels the pending delay, and a later relapse gets a fresh budget. Unauthorized is retried because the hub
 * shows a loading page for it while the app decides (#810); a rejection the server confirms goes to sign-in.
 * Runs on [dispatcher], as `UserAdmission` does (#177/#370), so [retry] never resumes on `Dispatchers.Main.immediate`.
 */
class HubAutoRetry(
    scope: CoroutineScope,
    health: Flow<ConnectionHealth>,
    visible: Flow<Boolean>,
    dispatcher: CoroutineDispatcher,
    private val retry: () -> Unit,
) {
    init {
        scope.launch(dispatcher) {
            // The re-probe's own Checking must not restart the backoff, or the loop never reaches its bound.
            combine(health.filter { it != ConnectionHealth.Checking }, visible) { current, onScreen -> onScreen && current.isRetryable() }
                .distinctUntilChanged()
                .collectLatest { shouldRetry -> if (shouldRetry) backoffRetries() }
        }
    }

    private suspend fun backoffRetries() {
        var delayMs = INITIAL_RETRY_DELAY_MS
        repeat(MAX_AUTO_RETRIES) {
            delay(delayMs)
            retry()
            delayMs = (delayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        }
    }
}

private fun ConnectionHealth.isRetryable(): Boolean =
    this == ConnectionHealth.Unreachable ||
        this == ConnectionHealth.LocalNetworkDenied ||
        this == ConnectionHealth.CouldNotLoad ||
        this == ConnectionHealth.Unauthorized
