package com.cooper.binge.companion.seerr.ui.state

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * A failed save as a snackbar that stays until it is answered: Retry sends the draft again. It goes by itself once a
 * save succeeds, whether that is the retry or a later change, which carries the failed one with it. A later change that
 * fails too leaves it showing.
 *
 * It is shown again whenever it goes without the user answering it, since an event's snackbar dismisses whatever is
 * showing, and after a retry: a retry that fails at once leaves [failed] true and this effect unrestarted. Leaving
 * [failed] cancels the effect, and a cancelled snackbar clears itself.
 *
 * Retry calls the latest [onRetry], not the one the effect started with: a caller whose retry reads what failed last
 * changes it while [failed] stays true.
 */
@Composable
fun SaveFailedSnackbar(
    failed: Boolean,
    snackbarHostState: SnackbarHostState,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    val currentOnRetry by rememberUpdatedState(onRetry)
    LaunchedEffect(failed) {
        if (!failed) return@LaunchedEffect
        while (true) {
            val result = snackbarHostState.showSnackbar(message, actionLabel = retryLabel, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) currentOnRetry()
        }
    }
}
