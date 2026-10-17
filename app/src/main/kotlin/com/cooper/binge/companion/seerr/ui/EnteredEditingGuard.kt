package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Calls [onDone] the first time [state] reads [SetupUiState.Connected] after editing has visibly begun,
 * for a screen that starts on a live connection being edited.
 *
 * The first reading can still be the pre-edit [SetupUiState.Connected], because `beginEdit()` is dispatched,
 * so the guard waits for a non-Connected reading first. [SetupUiState.Loading] is the StateFlow's seed,
 * so it does not count as editing either.
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
