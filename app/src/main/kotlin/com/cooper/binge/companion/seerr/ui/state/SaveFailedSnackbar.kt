package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * A failed save as a snackbar that stays until it is answered: Retry sends the draft again. It goes by itself once a
 * save succeeds, whether that is the retry or a later change, which carries the failed one with it. A later change that
 * fails too leaves it showing.
 *
 * It is shown again whenever it goes without the user answering it, since an event's snackbar dismisses whatever is
 * showing, and after a retry: a retry that fails at once leaves [failed] true and this effect unrestarted. Leaving
 * [failed] cancels the effect, and a cancelled snackbar clears itself.
 */
@Composable
fun SaveFailedSnackbar(
    failed: Boolean,
    snackbarHostState: SnackbarHostState,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        while (true) {
            val result = snackbarHostState.showSnackbar(message, actionLabel = retryLabel, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) onRetry()
        }
    }
}
