package io.github.scottcooper92.binge.seerr.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.LOCAL_NETWORK_PERMISSION

/**
 * What the "allow local network access" button does now, and what it says. [shortLabel] is for a row
 * of buttons with no room for the full words, such as the television board's.
 */
internal class AllowLocalNetwork(
    @StringRes val label: Int,
    @StringRes val shortLabel: Int,
    val run: () -> Unit,
)

/**
 * Asks for the local-network permission once, then sends the user to this app's Settings page: after
 * a refusal the system may not show the prompt again, and a button that does nothing is worse than
 * one that says where to go. [onChanged] runs when the prompt answers and when the user comes back
 * from Settings, so the caller reads the permission again.
 */
@Composable
internal fun rememberAllowLocalNetwork(onChanged: () -> Unit): AllowLocalNetwork {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (asked) onChanged() }
    return if (asked) {
        AllowLocalNetwork(R.string.local_network_open_settings, R.string.local_network_open_settings) { context.openAppSettings() }
    } else {
        AllowLocalNetwork(R.string.local_network_allow, R.string.local_network_allow_short) {
            asked = true
            launcher.launch(LOCAL_NETWORK_PERMISSION)
        }
    }
}

/** This app's own Settings page, where the permission can be switched on. A device with no such page does nothing. */
private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
