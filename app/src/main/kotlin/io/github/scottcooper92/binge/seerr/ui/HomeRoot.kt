package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Puts the home route that matches the connection at the root of the stack: [HubRoute] once a
 * server is connected, [HomeRoute] when none is. Null means the answer is still being worked out,
 * and leaves the stack alone.
 *
 * Only a home root is swapped, and only the root. The routes above it stay where they are, so a
 * notification's link that opened on [HomeRoute] keeps its section and detail when the hub arrives.
 */
internal fun NavBackStack<NavKey>.settleHome(connected: Boolean?) {
    val home =
        when (connected) {
            true -> HubRoute
            false -> HomeRoute
            null -> return
        }
    val root = firstOrNull()
    if ((root == HomeRoute || root == HubRoute) && root != home) set(0, home)
}
