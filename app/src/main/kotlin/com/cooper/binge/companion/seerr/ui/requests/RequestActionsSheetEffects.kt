package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/** [progress] without a failure from before this sheet opened: that one is not this sheet's to show, but its own tries are. */
@Composable
internal fun rememberOwnProgress(progress: ActionProgress?): ActionProgress? {
    val staleAttempt = remember { progress?.takeIf { it.failed }?.attempt }
    return progress?.takeUnless { it.failed && it.attempt == staleAttempt }
}

/**
 * Decline and Remove keep the sheet open while they run, so their control can show it. The sheet goes when one lands:
 * [landed] grew since the sheet first had one. A count, because the running state can come and go between two frames;
 * null while the page is not `Ready`, which is no count at all, never a 0 to compare against (#1788).
 */
@Composable
internal fun CloseWhenActionLands(
    landed: Int?,
    onDismiss: () -> Unit,
) {
    var openedAt by remember { mutableStateOf<Int?>(null) }
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(landed) {
        val count = landed ?: return@LaunchedEffect
        val first = openedAt ?: count.also { openedAt = it }
        if (count != first) currentDismiss()
    }
}
