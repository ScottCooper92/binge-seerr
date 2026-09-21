package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Calls [onDone] the first time [state] reads [SetupUiState.Connected] after editing has visibly
 * begun, for a screen that starts on a live connection being edited. [state]'s very first reading
 * can still be the pre-edit [SetupUiState.Connected] — the ViewModel's own `beginEdit()` update is
 * dispatched, not synchronous — so a raw "state is Connected" check would fire immediately; tracking
 * observed-non-Connected locally instead means [onDone] only fires on a Connected reading that
 * arrives after editing has begun, which is the one that means "saved". [SetupUiState.Loading] is
 * the StateFlow's seed value, read before that dispatch or the upstream combine have produced
 * anything real, so it must not count as "editing has begun" either.
 */
@Composable
fun rememberEnteredEditingGuard(
    state: SetupUiState,
    onDone: () -> Unit,
) {
    var enteredEditing by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is SetupUiState.Connected -> if (enteredEditing) onDone()
            is SetupUiState.Loading -> Unit
            else -> enteredEditing = true
        }
    }
}
