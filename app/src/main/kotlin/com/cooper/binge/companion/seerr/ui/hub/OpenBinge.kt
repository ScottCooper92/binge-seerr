package com.cooper.binge.companion.seerr.ui.hub

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.CompanionManifest
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.ui.openInBrowser

private const val PLAY_STORE_MARKET_URI = "market://details?id=${BingeHosts.RELEASE_PACKAGE_NAME}"

/**
 * Whether release Binge is installed, per the `<queries>` visibility declared for its package.
 *
 * The suppression is warranted: `getPackageInfo(String, Int)` is the only overload below API 33, and `minSdk` is 26.
 */
@Suppress("DEPRECATION")
fun Context.isBingeInstalled(): Boolean = attempt { packageManager.getPackageInfo(BingeHosts.RELEASE_PACKAGE_NAME, 0) }.isSuccess

/** Opens Binge itself, at wherever it last was; a failed launch is swallowed, since the hint offering it only shows where Binge is installed. */
fun Context.openBinge() {
    packageManager.getLaunchIntentForPackage(BingeHosts.RELEASE_PACKAGE_NAME)?.let { attempt { startActivity(it) } }
}

/** Opens Binge's Play Store listing in the Play Store app, or a browser where it is not present. */
fun Context.openBingeOnPlayStore() {
    attempt { startActivity(Intent(Intent.ACTION_VIEW, PLAY_STORE_MARKET_URI.toUri())) }
        .onFailure { openInBrowser(CompanionManifest.HOST_PLAY_LISTING_URL) }
}

/**
 * Opens Binge on its page for this companion, where the user turns it on: the platform's companion link
 * (`CompanionManifest.hostCompanionUri`), narrowed to release Binge. A Binge too old to answer it opens as [openBinge] does.
 */
fun Context.openBingeCompanions() {
    val link =
        Intent(
            Intent.ACTION_VIEW,
            CompanionManifest.hostCompanionUri(packageName).toUri(),
        ).setPackage(BingeHosts.RELEASE_PACKAGE_NAME)
    attempt { startActivity(link) }.onFailure { openBinge() }
}
