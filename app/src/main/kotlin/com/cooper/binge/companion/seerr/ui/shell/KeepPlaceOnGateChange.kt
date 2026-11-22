package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cooper.homelab.core.uiandroid.shell.TabShellState
import com.cooper.homelab.core.uiandroid.shell.popToRoot

/**
 * Flipping Discovery changes the shell under the app's stack and leaves the screen the user flipped it on. [resolved] is the
 * gate's value, null until read. Only a change after the first value is a toggle: the first is not, so a stack restored after
 * process death stays. A first value of false shows [SeerrTab.Settings], the tab an opted-out user has.
 */
@Composable
internal fun KeepPlaceOnGateChange(
    resolved: Boolean?,
    tabs: TabShellState<SeerrTab>,
) {
    var shown by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(resolved) {
        val now = resolved ?: return@LaunchedEffect
        val before = shown
        shown = now
        when {
            before == null && !now -> tabs.show(SeerrTab.Settings)
            before != null && before != now -> tabs.settleOnSettings()
        }
    }
}

/**
 * What a toggle changes under the open page: each tab's own stack goes back to its root and Settings shows, so the account tab is
 * selected with Discovery on. The app's stack is not touched. The page the user is on, and the path back from it, stay where they
 * are, and `settleHome` swaps the root beneath them.
 */
internal fun TabShellState<SeerrTab>.settleOnSettings() {
    tabs.forEach { stack(it).popToRoot() }
    show(SeerrTab.Settings)
}
