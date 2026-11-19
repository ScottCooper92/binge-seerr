package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Turning discovery off or on starts the app over from its start tab, with no stack left over from the other mode. [resolved] is the
 * gate's value, null until it has been read. Only a change after the first resolved value is a toggle: the first is not, so a stack
 * restored after process death or a link already applied stays. An opted-out user's first value shows Settings, the only tab there.
 */
@Composable
internal fun DiscoveryShellEffect(
    resolved: Boolean?,
    tabs: TabShellState<SeerrTab>,
    appStack: NavBackStack<NavKey>,
) {
    var shown by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(resolved) {
        val now = resolved ?: return@LaunchedEffect
        val before = shown
        shown = now
        when {
            before == null && !now -> tabs.show(SeerrTab.Settings)
            before != null && before != now -> tabs.restart(appStack)
        }
    }
}
