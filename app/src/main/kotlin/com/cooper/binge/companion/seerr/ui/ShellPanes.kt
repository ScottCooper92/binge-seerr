package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.binge.designsystem.R as DesR

private const val SHELL_ROLE = "seerr.shell"
private const val SECTION_ROLE = "seerr.section"
private const val FULL_SCREEN_ROLE = "seerr.fullScreen"

/** Marks a section's entry, so [ShellPanesStrategy] knows the stack above the shell starts with one and draws it beside the hub. */
internal val SectionPane: Map<String, Any> = mapOf(SECTION_ROLE to true)

/**
 * Marks an entry that covers the whole window, tab bar and nav pill included, even when it is opened from a section beside the hub.
 * [ShellPanesStrategy] leaves a stack whose top entry carries it to the single-pane scene, so Back returns to the section below.
 */
internal val FullScreenPane: Map<String, Any> = mapOf(FULL_SCREEN_ROLE to true)

/** Marks the shell's entry, so [ShellPanesStrategy] knows which entry holds the tab bar and the Settings tab. */
internal val ShellPane: Map<String, Any> = mapOf(SHELL_ROLE to true)

/**
 * What sits above the shell on the app's stack, for the Settings tab to draw beside its hub: [newest], whatever is on top. The
 * right pane shows it, so a section, what it opened and a page inside that each take the one pane in turn, and Back walks down.
 */
@Immutable
internal class ShellDetail(
    val newest: NavEntry<NavKey>?,
)

/** The detail the scene hands the shell's entry. Null outside [ShellPanesStrategy]'s scene. */
internal val LocalShellDetail = compositionLocalOf<ShellDetail?> { null }

/**
 * A wide window's shell: one scene for the shell's entry and the section above it, so the tab bar stays and the Settings tab
 * draws the section beside its hub. The sections stay on the app's stack at every width. Only how they are drawn changes with
 * the window, so a fold moves no entry and its ViewModel and scroll survive. A stack whose first entry above the shell is not a
 * section (a grid, a title page) is not this strategy's: that covers the whole window, as on a phone.
 */
internal object ShellPanesStrategy : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val shell = entries.firstOrNull()?.takeIf { it.metadata[SHELL_ROLE] == true } ?: return null
        val above = entries.drop(1)
        if (above.isNotEmpty() && above.first().metadata[SECTION_ROLE] != true) return null
        if (above.lastOrNull()?.metadata?.get(FULL_SCREEN_ROLE) == true) return null
        return ShellPanesScene(entries, shell, ShellDetail(above.lastOrNull()))
    }
}

private class ShellPanesScene(
    override val entries: List<NavEntry<NavKey>>,
    private val shell: NavEntry<NavKey>,
    private val detail: ShellDetail,
) : Scene<NavKey> {
    /** One key for every stack, so opening or closing a section swaps a pane's content rather than animating a new scene in. */
    override val key: Any = ShellPanesScene::class
    override val previousEntries: List<NavEntry<NavKey>> = entries.dropLast(1)

    override val content: @Composable () -> Unit = {
        CompositionLocalProvider(LocalShellDetail provides detail) { shell.Content() }
    }
}

/**
 * The Settings tab on a wide window, at every width: the [hub] on the left, and the right pane showing whatever is newest above
 * it, or [defaultSection] while nothing is open. The left pane never changes. What a section opens, and what that opens in turn,
 * replaces only the right pane (#2348).
 */
@Composable
internal fun SettingsPanes(
    hub: @Composable () -> Unit,
    defaultSection: @Composable () -> Unit,
) {
    val detail = LocalShellDetail.current
    TwoPaneRow(hub = hub, detail = { detail?.newest?.Content() ?: defaultSection() })
}

/** Two equal panes with the design system's gap between them. */
@Composable
internal fun TwoPaneRow(
    hub: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.pane_spacer)
    Row(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) { hub() }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
    }
}
