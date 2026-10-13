package com.cooper.binge.companion.seerr.ui

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.binge.companion.sdk.attempt

/** Opens [url] in a Custom Tab, or the default browser where none provides one. A device with neither is left alone. */
fun Context.openInBrowser(url: String) {
    attempt { CustomTabsIntent.Builder().build().launchUrl(this, url.toUri()) }
}
