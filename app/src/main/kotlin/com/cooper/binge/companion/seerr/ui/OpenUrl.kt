package com.cooper.binge.companion.seerr.ui

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.binge.companion.sdk.attempt

/** Opens [url] in a Custom Tab, or the default browser where none provides one. A device with neither is left alone. */
fun Context.openInBrowser(url: String) {
    attempt { CustomTabsIntent.Builder().build().launchUrl(this, url.toUri()) }
}

private const val DEBUG_APPLICATION_ID_SUFFIX = ".debug"

/**
 * Opens this app's Play Store listing, in the Play app where it is installed and in the browser where it is not. A
 * debug build's `.debug` suffix is dropped, so a development build opens the real release listing.
 */
fun Context.openPlayStoreListing() = openOnPlay("https://play.google.com/store/apps/details?id=${releasePackage()}")

/** Opens the page where a tester opts in to this app's beta, on the same terms as [openPlayStoreListing]. */
fun Context.openTestingOptIn() = openOnPlay("https://play.google.com/apps/testing/${releasePackage()}")

private fun Context.releasePackage(): String = packageName.removeSuffix(DEBUG_APPLICATION_ID_SUFFIX)

private fun Context.openOnPlay(url: String) {
    val viaPlay =
        attempt {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    if (viaPlay.isFailure) attempt { startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
