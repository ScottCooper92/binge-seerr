package io.github.scottcooper92.binge.seerr.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Subscribes to [events] before returning, and awaits the first one.
 *
 * A ViewModel's events are a `MutableSharedFlow` with no replay, so an emission made while nothing
 * is collecting is dropped — which makes `act(); vm.events.first()` a race the test loses by
 * hanging. It normally wins only because the action suspends on the network long enough for a
 * queued collector to start, which is not a property any test should depend on.
 *
 * [CoroutineStart.UNDISPATCHED] is what makes it deterministic rather than merely likelier: the
 * body runs on the caller's thread as far as its first suspension, and `first()` suspends *after*
 * registering. A plain `async` is queued on the test scheduler and has registered nothing yet.
 */
fun <T> CoroutineScope.awaitEvent(events: Flow<T>): Deferred<T> = async(start = CoroutineStart.UNDISPATCHED) { events.first() }
