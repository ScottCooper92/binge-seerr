package io.github.scottcooper92.binge.seerr.ui.hub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Fast while something is moving, slow while nothing is: the strip is the only thing that changes by the second. */
private const val ACTIVE_POLL_INTERVAL_MS = 10_000L
private const val IDLE_POLL_INTERVAL_MS = 60_000L

/** The strip is a teaser; the requests browser holds the full list. */
private const val STRIP_MAX_ITEMS = 5

/**
 * The "Downloading now" feed: while the hub is visible and the server [healthy], refreshed at once
 * and then on an interval that adapts to whether anything is downloading. Gating on health, not
 * merely on being connected, keeps the loop off a server the monitor already knows is down. The
 * ViewModel owns one on its scope, so the loop dies with it.
 */
class DownloadsPoller(
    scope: CoroutineScope,
    healthy: Flow<Boolean>,
    private val fetch: suspend () -> Result<List<HubDownload>>,
) {
    private val screenVisible = MutableStateFlow(false)
    private val state = MutableStateFlow<List<HubDownload>>(emptyList())
    val downloading: StateFlow<List<HubDownload>> = state.asStateFlow()

    init {
        scope.launch {
            combine(screenVisible, healthy) { visible, healthy -> visible && healthy }
                .distinctUntilChanged()
                .collectLatest { shouldPoll -> if (shouldPoll) poll() }
        }
    }

    fun setScreenVisible(visible: Boolean) {
        screenVisible.value = visible
    }

    private suspend fun poll() {
        while (true) {
            refresh()
            delay(if (state.value.isEmpty()) IDLE_POLL_INTERVAL_MS else ACTIVE_POLL_INTERVAL_MS)
        }
    }

    /** A failed fetch keeps the last strip: it is auxiliary, so it degrades silently. */
    private suspend fun refresh() {
        fetch().onSuccess { items -> state.value = items.take(STRIP_MAX_ITEMS) }
    }
}
