package com.cooper.binge.companion.seerr.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.shell.replaceAll

/**
 * Puts [HubRoute] at the root once a server is connected, and [HomeRoute] otherwise, unresolved included. With [shell] on, a
 * connected root is [ShellRoute], the tab bar over the hub. That is why this is a route swap: [HubRoute] is the list pane, so a
 * spinner there reads as half loaded, while [HomeRoute] takes the window (#815).
 *
 * Only a home root is swapped, so a link opened on [HomeRoute] keeps its routes above it while a connection is resolving or made.
 * Signed out, the link's pages that read the server are dropped, so they do not cover setup. A sign-out takes the stack to [HomeRoute].
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
        // A link that arrives signed out opens pages that read a server there is none of, over setup: dropped.
        root == HomeRoute && connected == false && any { it.readsServer() } -> replaceAll(filterNot { it.readsServer() })
        isHome && root != home -> set(0, home)
    }
}

/** Whether [this] is a page that reads the connected server, as a notification's or a hand-off's link opens. */
private fun NavKey.readsServer(): Boolean =
    this is RequestsRoute ||
        this is RequestDetailRoute ||
        this is IssuesRoute ||
        this is IssueDetailRoute ||
        this is UsersRoute ||
        this is UserDetailRoute
