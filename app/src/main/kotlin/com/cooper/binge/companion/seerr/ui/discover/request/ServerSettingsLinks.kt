package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.DvrInstanceRoute
import com.cooper.binge.companion.seerr.ui.settings.ServiceType

/**
 * How the request sheet reaches the settings of a server, for an administrator who finds the choices wrong: edit the chosen server,
 * or add one. The nav host provides the real one. The default is null, so a sheet composed without a host shows no such rows.
 */
class ServerSettingsLinks(
    val onEdit: (ServiceType, Int) -> Unit,
    val onAdd: (ServiceType) -> Unit,
)

val LocalServerSettingsLinks = compositionLocalOf<ServerSettingsLinks?> { null }

/** The links the nav host provides: a server's own settings page, or a new one of the kind. */
@Composable
internal fun rememberServerSettingsLinks(backStack: NavBackStack<NavKey>): ServerSettingsLinks =
    remember(backStack) {
        ServerSettingsLinks(
            onEdit = { type, id -> backStack.add(DvrInstanceRoute(type, id)) },
            onAdd = { type -> backStack.add(DvrInstanceRoute(type, null)) },
        )
    }
