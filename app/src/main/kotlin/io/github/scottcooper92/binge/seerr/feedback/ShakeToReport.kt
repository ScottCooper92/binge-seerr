package io.github.scottcooper92.binge.seerr.feedback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.binge.designsystem.component.BingeConfirmDialog
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser

/**
 * Offers to report a bug when the phone is shaken: a confirm first, so a shake in a pocket opens
 * nothing, and then the bug form on GitHub with [bugReportUrl]'s details filled in.
 */
@Composable
fun ShakeToReportPrompt(
    enabled: Boolean,
    bugReportUrl: () -> String,
) {
    val context = LocalContext.current
    var offering by rememberSaveable { mutableStateOf(false) }
    ShakeToReport(enabled = enabled && !offering) { offering = true }
    if (offering) {
        BingeConfirmDialog(
            title = stringResource(R.string.shake_report_title),
            message = stringResource(R.string.shake_report_message),
            confirmLabel = stringResource(R.string.settings_report_bug),
            onConfirm = {
                offering = false
                context.openInBrowser(bugReportUrl())
            },
            onDismiss = { offering = false },
        )
    }
}

/**
 * Listens for a shake while [enabled] and the screen is resumed. The sensor is released on pause,
 * so a backgrounded app never holds the accelerometer.
 */
@Composable
private fun ShakeToReport(
    enabled: Boolean,
    onShake: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detector = remember(context) { ShakeDetector(context) }
    val currentOnShake by rememberUpdatedState(onShake)
    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) return@DisposableEffect onDispose { detector.stop() }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> detector.start { currentOnShake() }
                    Lifecycle.Event.ON_PAUSE -> detector.stop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            detector.stop()
        }
    }
}
