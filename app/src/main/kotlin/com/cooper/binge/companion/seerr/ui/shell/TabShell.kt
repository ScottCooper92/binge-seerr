package com.cooper.binge.companion.seerr.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.component.BingeNavPresentation
import com.binge.designsystem.component.BingeNavSuiteItem
import com.binge.designsystem.component.BingeNavSuiteShell
import com.binge.designsystem.component.LocalTabReselectSignal
import com.binge.designsystem.motion.rememberBingeNavMotion
import com.cooper.binge.companion.seerr.ui.replaceAll
import kotlinx.coroutines.flow.MutableSharedFlow

/*
 * A tab shell with a back stack per tab. Nothing here names the app it serves: the tabs are whatever type the caller
 * passes, the stacks are the caller's, and the words and icons arrive as [BingeNavSuiteItem]s. It is written to move
 * to the shared library unchanged.
 */

/**
 * Which tab is showing, and each tab's own back stack. [tabs] are in the order the bar shows them. A tab keeps its stack
 * when another is selected, so coming back finds it as it was left. [startTab] is the one the app opens on, and the one Back
 * returns to: only Back at its root leaves the app.
 */
@Stable
class TabShellState<T : Any>(
    val tabs: List<T>,
    private val selectedIndex: MutableIntState,
    private val stacks: Map<T, NavBackStack<NavKey>>,
    val startTab: T = tabs.first(),
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

    /** Back at a tab's root: any other tab goes to the [startTab]. False at the start tab, where the caller leaves the app. */
    fun back(): Boolean {
        if (selected == startTab) return false
        show(startTab)
        return true
    }

    /**
     * A tap on the bar: another tab shows as it was left, and the tab already showing goes back to its root.
     * True when it was the tab already showing, so the caller can tell the tab to scroll to its top.
     */
    fun select(tab: T): Boolean {
        if (tab != selected) {
            show(tab)
            return false
        }
        stack(tab).popToRoot()
        return true
    }
}

/** Everything above the root comes off; the root stays, as a stack must never be empty. */
internal fun NavBackStack<NavKey>.popToRoot() {
    if (size > 1) replaceAll(listOf(first()))
}

/**
 * The state of a shell over [tabs], starting on [initial], which is also its start tab. The selected tab survives the process
 * being recreated; the stacks are the caller's [stacks], saved by whoever made them.
 */
@Composable
fun <T : Any> rememberTabShellState(
    tabs: List<T>,
    initial: T,
    stacks: Map<T, NavBackStack<NavKey>>,
): TabShellState<T> {
    val index = rememberSaveable { mutableIntStateOf(tabs.indexOf(initial).coerceAtLeast(0)) }
    return remember(tabs, stacks, index, initial) { TabShellState(tabs, index, stacks, startTab = initial) }
}

/**
 * [content] for the selected tab inside the design system's navigation shell. Each tab's composition state is saved when
 * another tab shows, so a list comes back scrolled where it was. [backToStart] is whether Back at a tab other than the start
 * tab goes to the start tab. A caller that draws something Back should close first, beside the shell's own content, turns it
 * off while that is open, so the handler of whatever holds it takes the Back.
 *
 * A tab change cross-fades by [transition]. A tap on the tab already showing pops it to its root and then sends the tab on
 * [LocalTabReselectSignal], so whatever shows the root scrolls to its top (`ScrollToTopOnTabReselect`).
 */
@Composable
fun <T : Any> TabShell(
    state: TabShellState<T>,
    items: List<BingeNavSuiteItem>,
    presentation: BingeNavPresentation,
    modifier: Modifier = Modifier,
    backToStart: Boolean = true,
    transition: ContentTransform = rememberBingeNavMotion().tabSwitch,
    content: @Composable (T) -> Unit,
) {
    val saveable = rememberSaveableStateHolder()
    val reselects = remember { MutableSharedFlow<Any>(extraBufferCapacity = 1) }
    BackHandler(enabled = backToStart && state.selected != state.startTab) { state.back() }
    BingeNavSuiteShell(
        items = items,
        selectedKey = state.selected,
        onSelect = { key ->
            val tab = state.tabs.firstOrNull { it == key }
            if (tab != null && state.select(tab)) reselects.tryEmit(tab)
        },
        modifier = modifier,
        presentation = presentation,
    ) {
        CompositionLocalProvider(LocalTabReselectSignal provides reselects) {
            AnimatedContent(targetState = state.selected, transitionSpec = { transition }, label = "tab") { tab ->
                saveable.SaveableStateProvider(state.tabs.indexOf(tab)) { content(tab) }
            }
        }
    }
}
