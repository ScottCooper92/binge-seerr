package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection

/**
 * The entry metadata that puts a screen in the detail pane beside the hub: a section, and everything
 * a section opens. They stack there in the order they were opened, so the pane shows the newest and
 * Back walks down through the rest. The hub is the list pane, on [HubRoute], a route of its own
 * because setup takes the whole window. On a narrow window each takes the whole window instead.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val DetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * The section a wide window shows beside the hub while none is open, so the detail pane is never
 * empty. Requests carries no visibility gate in [HubSection], so every user who reaches the hub has it.
 */
internal val DefaultSection: HubSection = HubSection.Requests

/**
 * The scene strategy for the hub and the detail pane beside it.
 *
 * Back pops one screen at a time. The library's default pops until the layout changes, and with the
 * hub at the root of the stack there is no earlier layout to change to: Back beside the hub would
 * pass every stacked screen and leave the app.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberSeerrPaneStrategy(directive: PaneScaffoldDirective): ListDetailSceneStrategy<NavKey> =
    rememberListDetailSceneStrategy(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = directive,
    )

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
 * Opens a section in place of whatever the hub has open. The open section goes, and so does
 * everything it stacked above itself, so Back from the new section returns to the hub rather than
 * walking every screen visited on the way here.
 *
 * [defaultShowing] is true when the detail pane is beside the hub, where [DefaultSection] shows
 * whenever nothing is open. Opening that section then clears the pane back to it, rather than
 * pushing a second copy of what is already on screen.
 */
internal fun NavBackStack<NavKey>.openSection(
    section: HubSection,
    defaultShowing: Boolean,
) {
    while (size > 1) removeLastOrNull()
    if (!(defaultShowing && section == DefaultSection)) add(section.route())
}

/**
 * The section the hub marks as open: the one on the stack, or [DefaultSection] while it is the
 * placeholder beside the hub. Nothing is marked when a screen other than a section fills the pane.
 */
internal fun List<NavKey>.selectedSection(defaultShowing: Boolean): HubSection? =
    firstNotNullOfOrNull { it.hubSection() } ?: DefaultSection.takeIf { defaultShowing && size == 1 }

/**
 * How many entries sit above [HubRoute] on the way to whatever is on screen now: 1 for a screen
 * pushed directly onto the hub, more for one stacked further above that. [HubRoute] not being on the
 * stack at all (a narrow window's own screens, or setup) counts as every entry being "above" it,
 * which is harmless: [paneShowsBack] only reads this once its own `hubBeside` is already true.
 */
internal fun List<NavKey>.paneDepth(): Int {
    val hubIndex = indexOfLast { it == HubRoute }
    return if (hubIndex == -1) size else size - hubIndex - 1
}

/**
 * The one back-arrow rule for the detail pane, whatever is filling it: hidden only when the hub is
 * showing beside the pane and this is the only thing stacked above it, because popping there would
 * not return the viewer anywhere they came from — the hub never left the screen. A section beside the
 * hub is always exactly that one entry, so this is the same rule the section screens already applied,
 * expressed once instead of twice.
 */
internal fun paneShowsBack(
    hubBeside: Boolean,
    paneDepth: Int,
): Boolean = !hubBeside || paneDepth > 1
