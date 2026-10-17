package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Puts [HubRoute] at the root once a server is connected, and [HomeRoute] otherwise, unresolved included.
 * That is why this is a route swap: [HubRoute] is the list pane, so a spinner there sits beside the detail
 * placeholder and reads as half loaded, while [HomeRoute] takes the window. A saved server restores
 * [HubRoute] before the answer arrives, so this is a state users meet, not a frame.
 *
 * Only a home root is swapped, so a notification's link opened on [HomeRoute] keeps its routes above it.
 */
internal fun NavBackStack<NavKey>.settleHome(connected: Boolean?) {
    val home = if (connected == true) HubRoute else HomeRoute
    val root = firstOrNull()
    if ((root == HomeRoute || root == HubRoute) && root != home) set(0, home)
}
