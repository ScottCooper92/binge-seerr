package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection

/**
 * The entry metadata that makes a section a detail pane beside the hub. The hub is the list pane
 * beside it, on [HubRoute], a route of its own because setup takes the whole window.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val SectionDetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/** The route a hub section opens. */
internal fun HubSection.route(): SeerrRoute =
    when (this) {
        HubSection.Requests -> RequestsRoute
        HubSection.Issues -> IssuesRoute
        HubSection.Users -> UsersRoute
        HubSection.Blocklist -> BlocklistRoute
        HubSection.Settings -> SettingsRoute
        else -> SectionRoute(this)
    }

/** The hub section this route is, or null for a route that is not one of them. */
internal fun NavKey.hubSection(): HubSection? =
    when (this) {
        RequestsRoute -> HubSection.Requests
        IssuesRoute -> HubSection.Issues
        UsersRoute -> HubSection.Users
        BlocklistRoute -> HubSection.Blocklist
        SettingsRoute -> HubSection.Settings
        is SectionRoute -> section
        else -> null
    }

/**
 * Opens a section, replacing the open one rather than stacking on it.
 *
 * Beside the hub that is what stops Back walking every section the user visited on the way here. On
 * a compact window it changes nothing: the hub is not on screen while a section is, so there is
 * never a section on top to replace.
 */
internal fun NavBackStack<NavKey>.openSection(section: HubSection) {
    if (lastOrNull()?.hubSection() != null) removeLastOrNull()
    add(section.route())
}
