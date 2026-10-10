package io.github.scottcooper92.binge.seerr.ui

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import io.github.scottcooper92.binge.seerr.seerr.attempt

/** Opens [url] in a Custom Tab, or the default browser where none provides one. A device with neither is left alone. */
fun Context.openInBrowser(url: String) {
    attempt { CustomTabsIntent.Builder().build().launchUrl(this, url.toUri()) }
}
