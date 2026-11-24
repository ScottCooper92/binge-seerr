package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.DvrInstanceRoute
import com.cooper.binge.companion.seerr.ui.settings.ServiceType

/**
 * How the request sheet reaches a new server's settings page, for an administrator whose servers are not the ones wanted. The nav
 * host provides the real one. The default is null, so a sheet composed without a host shows no Add a server.
 */
class ServerSettingsLinks(
    val onAdd: (ServiceType) -> Unit,
)

val LocalServerSettingsLinks = compositionLocalOf<ServerSettingsLinks?> { null }

/** The link the nav host provides: a new server's settings page of the kind. */
@Composable
internal fun rememberServerSettingsLinks(backStack: NavBackStack<NavKey>): ServerSettingsLinks =
    remember(backStack) {
        ServerSettingsLinks(
            onAdd = { type -> backStack.add(DvrInstanceRoute(type, null)) },
        )
    }
