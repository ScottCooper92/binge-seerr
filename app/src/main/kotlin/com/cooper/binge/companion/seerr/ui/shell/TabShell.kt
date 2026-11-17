package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.component.BingeNavPresentation
import com.binge.designsystem.component.BingeNavSuiteItem
import com.binge.designsystem.component.BingeNavSuiteShell
import com.cooper.binge.companion.seerr.ui.replaceAll

/*
 * A tab shell with a back stack per tab. Nothing here names the app it serves: the tabs are whatever type the caller
 * passes, the stacks are the caller's, and the words and icons arrive as [BingeNavSuiteItem]s. It is written to move
 * to the shared library unchanged.
 */

/**
 * Which tab is showing, and each tab's own back stack. [tabs] are in the order the bar shows them. A tab keeps its stack
 * when another is selected, so coming back finds it as it was left.
 */
@Stable
class TabShellState<T : Any>(
    val tabs: List<T>,
    private val selectedIndex: MutableIntState,
    private val stacks: Map<T, NavBackStack<NavKey>>,
) {
    init {
        require(tabs.isNotEmpty() && tabs.all { it in stacks }) { "every tab needs a stack" }
    }

    val selected: T get() = tabs[selectedIndex.intValue]

    fun stack(tab: T): NavBackStack<NavKey> = stacks.getValue(tab)

    /** Shows [tab] as it was left. For a link or a restore: nothing is popped. */
    fun show(tab: T) {
        selectedIndex.intValue = tabs.indexOf(tab).coerceAtLeast(0)
    }

    /** A tap on the bar: another tab shows as it was left, and the tab already showing goes back to its root. */
    fun select(tab: T) {
        if (tab == selected) stack(tab).popToRoot() else show(tab)
    }
}

/** Everything above the root comes off; the root stays, as a stack must never be empty. */
internal fun NavBackStack<NavKey>.popToRoot() {
    if (size > 1) replaceAll(listOf(first()))
}

/**
 * The state of a shell over [tabs], starting on [initial]. The selected tab survives the process being recreated; the
 * stacks are the caller's [stacks], saved by whoever made them.
 */
@Composable
fun <T : Any> rememberTabShellState(
    tabs: List<T>,
    initial: T,
    stacks: Map<T, NavBackStack<NavKey>>,
): TabShellState<T> {
    val index = rememberSaveable { mutableIntStateOf(tabs.indexOf(initial).coerceAtLeast(0)) }
    return TabShellState(tabs, index, stacks)
}

/**
 * [content] for the selected tab inside the design system's navigation shell. Each tab's composition state is saved when
 * another tab shows, so a list comes back scrolled where it was.
 */
@Composable
fun <T : Any> TabShell(
    state: TabShellState<T>,
    items: List<BingeNavSuiteItem>,
    presentation: BingeNavPresentation,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val saveable = rememberSaveableStateHolder()
    BingeNavSuiteShell(
        items = items,
        selectedKey = state.selected,
        onSelect = { key -> state.tabs.firstOrNull { it == key }?.let(state::select) },
        modifier = modifier,
        presentation = presentation,
    ) {
        val tab = state.selected
        saveable.SaveableStateProvider(state.tabs.indexOf(tab)) { content(tab) }
    }
}
