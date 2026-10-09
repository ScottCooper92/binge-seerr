package io.github.scottcooper92.binge.seerr.ui.hub

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.binge.companion.sdk.BingeHosts
import io.github.scottcooper92.binge.seerr.ui.openInBrowser

private const val PLAY_STORE_WEB_URL = "https://play.google.com/store/apps/details?id=${BingeHosts.RELEASE_PACKAGE_NAME}"
private const val PLAY_STORE_MARKET_URI = "market://details?id=${BingeHosts.RELEASE_PACKAGE_NAME}"

/**
 * Whether release Binge is installed, per the `<queries>` visibility declared for its package.
 *
 * The suppression is warranted: `getPackageInfo(String, Int)` is the only overload below API 33, and `minSdk` is 26.
 */
@Suppress("DEPRECATION")
fun Context.isBingeInstalled(): Boolean = runCatching { packageManager.getPackageInfo(BingeHosts.RELEASE_PACKAGE_NAME, 0) }.isSuccess

/** Opens Binge itself, at wherever it last was; a failed launch is swallowed, since the hint offering it only shows where Binge is installed. */
fun Context.openBinge() {
    packageManager.getLaunchIntentForPackage(BingeHosts.RELEASE_PACKAGE_NAME)?.let { runCatching { startActivity(it) } }
}

/** Opens Binge's Play Store listing in the Play Store app, or a browser where it is not present. */
fun Context.openBingeOnPlayStore() {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, PLAY_STORE_MARKET_URI.toUri())) }
        .onFailure { openInBrowser(PLAY_STORE_WEB_URL) }
}
