package io.github.scottcooper92.binge.seerr.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** How often a row's "in 20 minutes" is worded again. */
private const val MINUTE_MILLIS = 60_000L

/**
 * The time from [clock] now, then again at each minute, for as long as it is collected. A view model puts it in its
 * `Ready` state so a relative time ("Next run in 20 minutes") counts down between reads, and collects it only while
 * its screen is showing, so nothing ticks off screen (#935, #989).
 */
internal fun minuteClock(
    clock: () -> Long = System::currentTimeMillis,
    ticker: Ticker = Ticker(),
): Flow<Long> =
    flow {
        while (true) {
            val now = clock()
            emit(now)
            ticker.await(MINUTE_MILLIS - now % MINUTE_MILLIS)
        }
    }
