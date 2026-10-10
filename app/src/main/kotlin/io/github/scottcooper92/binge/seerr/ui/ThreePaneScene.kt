package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.binge.designsystem.LocalPaneDepth
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import com.binge.designsystem.R as DesR

private const val PANE_ROLE = "seerr.threePane.role"

private enum class PaneRole { Hub, Section }

/** Marks the hub's entry, so [ThreePaneStrategy] knows which entry is the first pane. */
internal val ThreePaneHub: Map<String, Any> = mapOf(PANE_ROLE to PaneRole.Hub)

/** Marks a section's entry, so [ThreePaneStrategy] knows which entry is the second pane. */
internal val ThreePaneSection: Map<String, Any> = mapOf(PANE_ROLE to PaneRole.Section)

/**
 * Whether the window is a landscape tablet, where the hub, a section and what it opened sit side by side (#1110). The
 * design system's own line for an expanded rail: at least 1000dp wide and a smallest width of at least 600dp, the
 * same line Binge's tablet rail uses. A resource, so a rotation answers again.
 */
@Composable
internal fun isThreePaneWindow(): Boolean = booleanResource(DesR.bool.binge_nav_rail_expanded)

/**
 * The hub, the newest section above it, and the newest entry above that, as three equal panes. A stack without the hub,
 * setup's, is not this strategy's to lay out. [defaultSection] fills the second pane while no section is on the stack,
 * which [SeerrPaneHost] keeps to a frame by putting [DefaultSection] there itself.
 *
 * A custom scene rather than Material adaptive's list-detail-extra: in adaptive 1.3.0 only the list pane can name a
 * placeholder, so while a section has nothing open the library drops to two panes and widens the list, where three
 * panes are wanted all the time. Revisit this if a later release gives the extra pane a placeholder.
 */
internal class ThreePaneStrategy(
    private val defaultSection: @Composable () -> Unit,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val hub = entries.indexOfLast { it.metadata[PANE_ROLE] == PaneRole.Hub }
        if (hub == -1) return null
        val section = entries.indexOfLast { it.metadata[PANE_ROLE] == PaneRole.Section }.takeIf { it > hub }
        val anchor = section ?: hub
        val item = entries.lastIndex.takeIf { it > anchor }
        return ThreePaneScene(
            entries = entries,
            hub = entries[hub],
            section = section?.let(entries::get),
            item = item?.let(entries::get),
            itemDepth = entries.lastIndex - anchor,
            defaultSection = defaultSection,
        )
    }
}

/**
 * Each pane is told its own depth, so the design system's Back rule reads the right one in each: the section and what it
 * opened never leave the screen, so neither offers Back, while anything stacked above that item does.
 */
private class ThreePaneScene(
    override val entries: List<NavEntry<NavKey>>,
    private val hub: NavEntry<NavKey>,
    private val section: NavEntry<NavKey>?,
    private val item: NavEntry<NavKey>?,
    private val itemDepth: Int,
    private val defaultSection: @Composable () -> Unit,
) : Scene<NavKey> {
    // One key for every stack, so opening or closing an item swaps a pane's content rather than animating a new scene in.
    override val key: Any = ThreePaneScene::class
    override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)

    override val content: @Composable () -> Unit = {
        ThreePaneRow(
            hub = { hub.Content() },
            section = {
                CompositionLocalProvider(LocalPaneDepth provides 1) { section?.Content() ?: defaultSection() }
            },
            item = {
                CompositionLocalProvider(LocalPaneDepth provides itemDepth) { item?.Content() ?: NothingOpen() }
            },
        )
    }
}

/** Three equal panes with the design system's gap between them. The scene's layout, and the frames'. */
@Composable
internal fun ThreePaneRow(
    hub: @Composable () -> Unit,
    section: @Composable () -> Unit,
    item: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.pane_spacer)
    Row(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { hub() }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f).fillMaxHeight()) { section() }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f).fillMaxHeight()) { item() }
    }
}

/** The third pane while the section beside it has opened nothing. */
@Composable
internal fun NothingOpen() =
    DetailPaneContent {
        EmptyScreen(
            title = stringResource(R.string.three_pane_nothing_open_title),
            message = stringResource(R.string.three_pane_nothing_open_message),
        )
    }

/**
 * Opens [route] in the third pane in place of what was there: everything above the open section goes first, or above
 * the hub when no section is on the stack. What an item opens in turn still stacks above it, through a plain add.
 */
internal fun NavBackStack<NavKey>.openBeside(route: NavKey) {
    val section = indexOfLast { it.hubSection() != null }
    val anchor = if (section >= 0) section else indexOfLast { it == HubRoute }
    while (lastIndex > anchor && size > 1) removeLastOrNull()
    add(route)
}
