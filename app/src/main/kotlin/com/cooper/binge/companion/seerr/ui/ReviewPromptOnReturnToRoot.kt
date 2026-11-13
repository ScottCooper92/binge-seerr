package com.cooper.binge.companion.seerr.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cooper.binge.companion.seerr.review.ReviewPromptViewModel

/**
 * Asks for a Play review when the user comes back to the root after leaving it, so never mid-task and never over a
 * screen of their own making. Whether to show anything is the prompt's to decide. Only a real navigation away from a
 * connected root counts as leaving: starting the app, or signing in, passes through states that are not the root.
 * The ask runs inside the effect, so losing the connection while it is in flight, as Disconnect does, cancels it
 * before the card shows.
 */
@Composable
internal fun ReviewPromptOnReturnToRoot(
    connected: Boolean?,
    stackDepth: Int,
    viewModel: ReviewPromptViewModel = hiltViewModel(),
) = ReturnToRootEffect(connected, stackDepth, viewModel::onReturnedToRoot)

@Composable
internal fun ReturnToRootEffect(
    connected: Boolean?,
    stackDepth: Int,
    onReturned: suspend (Activity) -> Unit,
) {
    val activity = LocalActivity.current
    var hasLeftRoot by rememberSaveable { mutableStateOf(false) }
    val ask by rememberUpdatedState<suspend () -> Unit> { activity?.let { onReturned(it) } }
    val connectedRoot = connected == true && stackDepth <= 1
    val deeper = connected == true && stackDepth > 1
    LaunchedEffect(connectedRoot, deeper) {
        if (deeper) {
            hasLeftRoot = true
        } else if (connected != true) {
            hasLeftRoot = false
        } else if (connectedRoot && hasLeftRoot) {
            hasLeftRoot = false
            ask()
        }
    }
}
