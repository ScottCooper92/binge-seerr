package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.core.uiandroid.shell.replaceAll

/**
 * Puts [HubRoute] at the root once a server is connected, and [HomeRoute] otherwise, unresolved included. With [shell] on, a
 * connected root is [ShellRoute], the tab bar over the hub. That is why this is a route swap: [HubRoute] is the list pane, so a
 * spinner there reads as half loaded, while [HomeRoute] takes the window (#815).
 *
 * Only a home root is swapped, so a link opened on [HomeRoute] keeps its routes above it. A sign-out takes the stack to [HomeRoute].
 */
internal fun NavBackStack<NavKey>.settleHome(
    connected: Boolean?,
    shell: Boolean = false,
) {
    val connectedRoot = if (shell) ShellRoute else HubRoute
    val home = if (connected == true) connectedRoot else HomeRoute
    val root = firstOrNull()
    val isHome = root == HomeRoute || root == HubRoute || root == ShellRoute
    when {
        // Signed out from the hub: what it had open belongs to the server just left, and would cover setup (#1760).
        (root == HubRoute || root == ShellRoute) && connected == false -> replaceAll(listOf(HomeRoute))
        isHome && root != home -> set(0, home)
    }
}
