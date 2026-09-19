package io.github.scottcooper92.binge.seerr.ui.hub

import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * How [DownloadsPoller] waits between refreshes: a real, unbounded [delay] in production. A test
 * that takes `MainDispatcherRule` subclasses this to stop the loop after the refresh it needs,
 * rather than let a virtual clock spin it forever (#337).
 */
open class DownloadsPollerTicker
    @Inject
    constructor() {
        open suspend fun await(intervalMs: Long) = delay(intervalMs)
    }
