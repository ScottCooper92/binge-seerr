package com.cooper.binge.companion.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.binge.designsystem.PaneBackNavigationBehavior
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.binge.designsystem.R as DesR

/**
 * The entry metadata that puts a screen in the detail pane beside the hub: a section, and everything
 * a section opens. They stack there in the order they were opened, so the pane shows the newest and
 * Back walks down through the rest. The hub is the list pane, on [HubRoute], a route of its own
 * because setup takes the whole window. On a narrow window each takes the whole window instead.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val DetailPane: Map<String, Any> = ListDetailSceneStrategy.detailPane()

/**
 * The content of a [DetailPane] entry: a pane whose start edge is the one it shares with the hub, so that edge takes the
 * design system's narrow inner inset and leaves the window's insets to the hub's side (#814). Every detail entry
 * uses it, so a route added later cannot forget it. Alone in a narrow window it is a whole-window screen as before.
 */
@Composable
internal fun DetailPaneContent(content: @Composable () -> Unit) = PaneContent(innerEdge = PaneEdge.Start, content = content)

/**
 * The section a wide window shows beside the hub while none is open, so the detail pane is never
 * empty. Requests carries no visibility gate in [HubSection], so every user who reaches the hub has it.
 */
internal val DefaultSection: HubSection = HubSection.Requests

/**
 * The scene strategy for the hub and the detail pane beside it.
 *
 * [PaneBackNavigationBehavior] (design-system, shared with Binge): the library's default pops until the
 * layout changes, and with the hub at the root there is none to change to, so Back would leave the app.
 *
 * @param backStack read for the one case the library can't see: [DefaultSection] opened beside the hub,
 *   where popping it would land on its own placeholder and look like Back did nothing (#815).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberSeerrPaneStrategy(
    directive: PaneScaffoldDirective,
    backStack: List<NavKey>,
    shellBeside: Boolean = false,
): SceneStrategy<NavKey> {
    // The design system's gap between the panes in place of Material adaptive's own 24dp (#814), as Binge's is.
    val spaced = directive.copy(horizontalPartitionSpacerSize = dimensionResource(DesR.dimen.pane_spacer))
    val listDetail =
        rememberListDetailSceneStrategy<NavKey>(
            backNavigationBehavior = PaneBackNavigationBehavior,
            directive = spaced.copy(defaultPanePreferredWidth = equalPaneWidth(spaced)),
        )
    return remember(listDetail, backStack, shellBeside) {
        SeerrPaneStrategy(listDetail, backStack, shell = if (shellBeside) ShellPanesStrategy else null)
    }
}

/**
 * The shell's scenes when it has one, else [listDetail]'s, except that one showing the hub beside the default
 * section that was opened on purpose claims no Back. The system takes it and leaves the app, as it does from the
 * placeholder, which looks the same. Every scene is wrapped, so a scene's type never changes with the stack and the
 * panes don't animate as if it had.
 */
private class SeerrPaneStrategy(
    private val listDetail: SceneStrategy<NavKey>,
    private val backStack: List<NavKey>,
    private val shell: SceneStrategy<NavKey>? = null,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val shellScene = shell?.let { with(it) { calculateScene(entries) } }
        val scene =
            shellScene
                ?: with(listDetail) { calculateScene(entries) }
                ?: return null
        // toList(): a NavBackStack is a list by delegation, without a list's equality.
        val defaultBesideHub =
            scene.entries.size > 1 && backStack.toList().let { it.size == 2 && it[0].isPaneRoot() && it[1] == DefaultSection.route() }
        return SeerrPaneScene(scene, claimsBack = !defaultBesideHub, contentTakesBack = shellScene != null)
    }
}

/**
 * Not claiming Back takes two things: NavDisplay claims it while a scene has [previousEntries], and the list-detail
 * scene registers a handler of its own in its content, so that content runs under a dispatcher that is switched off.
 * The shell's scene has no such handler, and its content has one that should answer here: the tab shell's, which takes Back
 * from the bare hub to the start tab. So with [contentTakesBack] the content keeps its dispatcher (#2346).
 */
private class SeerrPaneScene(
    scene: Scene<NavKey>,
    claimsBack: Boolean,
    contentTakesBack: Boolean = false,
) : Scene<NavKey> by scene {
    override val previousEntries: List<NavEntry<NavKey>> = if (claimsBack) scene.previousEntries else emptyList()

    override val content: @Composable () -> Unit =
        if (contentTakesBack) {
            scene.content
        } else {
            {
                val owner = rememberNavigationEventDispatcherOwner(enabled = claimsBack)
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) { scene.content() }
            }
        }
}

/** Half the window less half the gap between the panes, so the gap sits at the window's centre. */
@Composable
private fun equalPaneWidth(directive: PaneScaffoldDirective): Dp {
    val windowWidth =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.width
                .toDp()
        }
    return (windowWidth - directive.horizontalPartitionSpacerSize) / 2
}

/** The root the hub sits at: [HubRoute] beside the sections, or [ShellRoute], whose Settings tab holds the hub. */
internal fun NavKey?.isPaneRoot(): Boolean = this == HubRoute || this == ShellRoute

/** Only the pane root is on the stack: nothing is open beside the hub. */
internal fun List<NavKey>.isOnlyPaneRoot(): Boolean = size == 1 && first().isPaneRoot()

/**
 * The bare hub: nothing is open beside it, or only the default section, which the user opened on purpose and which shows the
 * same screen as the placeholder. Back from here leaves for the start tab; from anything above it, Back closes that first (#2346).
 */
internal fun List<NavKey>.nothingOpenBesideHub(): Boolean =
    isOnlyPaneRoot() || (size == 2 && first().isPaneRoot() && this[1] == DefaultSection.route())

/**
 * A page pushed straight over the shell that is not a section, a title or a grid: [ShellPanesStrategy] leaves it to cover the
 * window, so no hub is beside it and it carries its own Back.
 */
internal fun List<NavKey>.coversWindow(): Boolean = size > 1 && first() == ShellRoute && !this[1].opensBesideHub()

/**
 * Whether [ShellPanesStrategy] draws this route in the Settings tab's content pane when it is the first thing above the shell: a hub
 * section, or the settings search. The strategy's marker, the stash and [coversWindow] all ask this, so they cannot disagree.
 */
internal fun NavKey.opensBesideHub(): Boolean = hubSection() != null || this == SettingsSearchRoute

/** The route a hub section opens. */
internal fun HubSection.route(): SeerrRoute =
    when (this) {
        HubSection.Requests -> RequestsRoute
        HubSection.Issues -> IssuesRoute
        HubSection.Users -> UsersRoute
        HubSection.Blocklist -> BlocklistRoute
        HubSection.Settings -> SettingsRoute
        HubSection.ThisApp -> ThisAppRoute()
    }

/** The hub section this route is, or null for a route that is not one of them. */
internal fun NavKey.hubSection(): HubSection? =
    when (this) {
        RequestsRoute -> HubSection.Requests
        IssuesRoute -> HubSection.Issues
        UsersRoute -> HubSection.Users
        BlocklistRoute -> HubSection.Blocklist
        SettingsRoute -> HubSection.Settings
        is ThisAppRoute -> HubSection.ThisApp
        else -> null
    }

/**
 * Opens a section in place of whatever the hub has open. The open section goes, and so does
 * everything it stacked above itself, so Back from the new section returns to the hub rather than
 * walking every screen visited on the way here.
 *
 * The section is pushed even when it is [DefaultSection] already showing as the placeholder beside the hub, so the
 * stack records what the user chose and a narrower window keeps showing it (#815).
 */
internal fun NavBackStack<NavKey>.openSection(section: HubSection) {
    while (size > 1) removeLastOrNull()
    add(section.route())
}

/**
 * Opens [route] from the [DefaultSection] standing in as the placeholder beside the hub. The section goes on the stack
 * beneath it first, so Back, and a narrower window, return to the list it was opened from rather than to the hub (#815).
 */
internal fun NavBackStack<NavKey>.openAboveDefault(route: NavKey) {
    if (lastOrNull().isPaneRoot()) add(DefaultSection.route())
    add(route)
}

/**
 * The section the hub marks as open: the one on the stack, or [DefaultSection] while it is the
 * placeholder beside the hub. Nothing is marked when a screen other than a section fills the pane: the settings search, or a page
 * opened from one of its results, even with a section still open beneath it. The mark returns when the search closes (#2380).
 */
internal fun List<NavKey>.selectedSection(defaultShowing: Boolean): HubSection? =
    if (SettingsSearchRoute in
        this
    ) {
        null
    } else {
        firstNotNullOfOrNull { it.hubSection() } ?: DefaultSection.takeIf { defaultShowing && size == 1 }
    }

/**
 * How many entries sit above [HubRoute] on the way to whatever is on screen now: 1 for a screen
 * pushed directly onto the hub, more for one stacked further above that. [HubRoute] not being on the
 * stack at all (a narrow window's own screens, or setup) counts as every entry being "above" it,
 * which is harmless: the design system's `paneShowsBack` only reads this once its own `hubBeside`
 * is already true.
 */
internal fun List<NavKey>.paneDepth(): Int {
    val hubIndex = indexOfLast { it.isPaneRoot() }
    return if (hubIndex == -1) size else size - hubIndex - 1
}
