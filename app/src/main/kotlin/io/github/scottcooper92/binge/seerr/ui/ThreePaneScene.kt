package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import com.binge.designsystem.R as DesR

// PROTOTYPE: the landscape-tablet layout. The hub, the open section and what that section opened always sit side by
// side; a section that has opened nothing yet shows a placeholder in the third pane.

private const val PANE_ROLE = "seerr.threePane.role"

/** Marks the hub's entry for [ThreePaneStrategy]. */
internal val ThreePaneHubTag: Map<String, Any> = mapOf(PANE_ROLE to "hub")

/** Marks a section's entry for [ThreePaneStrategy]. */
internal val ThreePaneSectionTag: Map<String, Any> = mapOf(PANE_ROLE to "section")

/**
 * The hub in the first pane, the newest section above it in the second ([defaultSection] while none is open), and the
 * newest entry above that in the third. A stack without the hub, setup's, is not this strategy's to lay out.
 */
internal class ThreePaneStrategy(
    private val defaultSection: @Composable () -> Unit,
) {
    fun calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val hubIndex = entries.indexOfLast { it.metadata[PANE_ROLE] == "hub" }
        if (hubIndex == -1) return null
        val sectionIndex = entries.indexOfLast { it.metadata[PANE_ROLE] == "section" }.takeIf { it > hubIndex }
        val itemIndex = entries.lastIndex.takeIf { it > (sectionIndex ?: hubIndex) }
        return ThreePaneScene(
            entries = entries,
            hub = entries[hubIndex],
            section = sectionIndex?.let(entries::get),
            item = itemIndex?.let(entries::get),
            defaultSection = defaultSection,
        )
    }
}

private class ThreePaneScene(
    override val entries: List<NavEntry<NavKey>>,
    private val hub: NavEntry<NavKey>,
    private val section: NavEntry<NavKey>?,
    private val item: NavEntry<NavKey>?,
    private val defaultSection: @Composable () -> Unit,
) : Scene<NavKey> {
    // One key for every stack, so opening and closing panes swaps their content rather than animating a new scene in.
    override val key: Any = ThreePaneScene::class
    override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)

    override val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.pane_spacer))) {
            Box(Modifier.weight(1f).fillMaxHeight()) { hub.Content() }
            Box(Modifier.weight(1f).fillMaxHeight()) { section?.Content() ?: defaultSection() }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                item?.Content() ?: DetailPaneContent {
                    EmptyScreen(title = "Nothing open", message = "Pick something from the list to see it here.")
                }
            }
        }
    }
}
