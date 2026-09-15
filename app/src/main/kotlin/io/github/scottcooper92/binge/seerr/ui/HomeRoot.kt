package io.github.scottcooper92.binge.seerr.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Puts the home route that matches the connection at the root of the stack: [HubRoute] once a
 * server is connected, and [HomeRoute] otherwise.
 *
 * Unresolved counts as otherwise, which is the whole reason this is a route swap and not a branch
 * inside one entry. [HubRoute] is the list pane, so a spinner drawn there renders beside the detail
 * pane's placeholder and the app reads as half loaded. [HomeRoute] carries no pane metadata and
 * takes the window, which is what connecting should look like. A saved server restores [HubRoute] at
 * the root before the answer arrives, so without this it is the state a user actually meets rather
 * than a frame.
 *
 * Only a home root is swapped, and only the root. The routes above it stay where they are, so a
 * notification's link that opened on [HomeRoute] keeps its section and detail when the hub arrives.
 */
internal fun NavBackStack<NavKey>.settleHome(connected: Boolean?) {
    val home = if (connected == true) HubRoute else HomeRoute
    val root = firstOrNull()
    if ((root == HomeRoute || root == HubRoute) && root != home) set(0, home)
}
