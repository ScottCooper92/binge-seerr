package io.github.scottcooper92.binge.seerr.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/**
 * Puts [text] on the clipboard under [label]. [sensitive] marks it so the system's clipboard
 * preview does not show it (API 33+), which is what a secret such as the API key wants.
 */
fun Context.copyToClipboard(
    label: String,
    text: String,
    sensitive: Boolean = false,
) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}
