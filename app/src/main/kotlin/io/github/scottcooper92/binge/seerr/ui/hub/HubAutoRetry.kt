package io.github.scottcooper92.binge.seerr.ui.hub

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
 * Heals a transient failure in place: while the hub is on screen and its health is retryable
 * ([ConnectionHealth.Unreachable] or [ConnectionHealth.CouldNotLoad]) it re-probes on a bounded
 * exponential backoff, so a network that comes up a moment after launch recovers by itself.
 * `collectLatest` restarts on every change, so leaving the retryable set or the screen cancels the
 * in-flight delay and a later relapse gets a fresh budget.
 */
class HubAutoRetry(
    scope: CoroutineScope,
    health: Flow<ConnectionHealth>,
    visible: Flow<Boolean>,
    private val retry: () -> Unit,
) {
    init {
        scope.launch {
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

private fun ConnectionHealth.isRetryable(): Boolean = this == ConnectionHealth.Unreachable || this == ConnectionHealth.CouldNotLoad
