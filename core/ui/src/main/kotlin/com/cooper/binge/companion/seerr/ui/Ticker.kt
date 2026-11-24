package com.cooper.binge.companion.seerr.ui

import kotlinx.coroutines.delay

/**
 * How a loop that runs while its screen shows waits for its next turn: a real [delay] in production. The minute clock
 * and the downloads poll both wait through it, and the app's Hilt module provides it to the view models that run them. A test that takes
 * `MainDispatcherRule` passes one that never returns after the turn it needs, rather than let a virtual clock spin the
 * loop for ever (#337).
 */
open class Ticker {
    open suspend fun await(millis: Long) = delay(millis)
}
