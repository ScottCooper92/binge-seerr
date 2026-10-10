package io.github.scottcooper92.binge.seerr.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** How often a row's "in 20 minutes" is worded again. */
private const val MINUTE_MILLIS = 60_000L

/**
 * How [minuteClock] waits for the next minute: a real [delay] in production. A test that takes `MainDispatcherRule`
 * passes one that never returns, rather than let a virtual clock spin the loop for ever (#337).
 */
open class MinuteTicker
    @Inject
    constructor() {
        open suspend fun await(millis: Long) = delay(millis)
    }

/**
 * The time from [clock] now, then again at each minute, for as long as it is collected. A view model puts it in its
 * `Ready` state so a relative time ("Next run in 20 minutes") counts down between reads, and collects it only while
 * its screen is showing, so nothing ticks off screen (#935, #989).
 */
internal fun minuteClock(
    clock: () -> Long = System::currentTimeMillis,
    ticker: MinuteTicker = MinuteTicker(),
): Flow<Long> =
    flow {
        while (true) {
            val now = clock()
            emit(now)
            ticker.await(MINUTE_MILLIS - now % MINUTE_MILLIS)
        }
    }
