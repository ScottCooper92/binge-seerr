package com.cooper.binge.companion.seerr.ui.tv

import android.content.Context
import android.content.Intent
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.ui.MediaServerItem

/** The extra a media server's TV app reads the item to show from (jellyfin-androidtv's `StartupActivity.EXTRA_ITEM_ID`). */
internal const val MEDIA_SERVER_EXTRA_ITEM_ID = "ItemId"

/**
 * A media server's Android TV app, and the activity that opens one item on its details page. The manifest's `queries`
 * name each package, so whether one is installed is visible. Jellyfin's is checked against its source and on a device. Emby's
 * is the same shape, taken from an older copy of its source, and not yet checked on a device.
 */
internal enum class TvMediaServerApp(
    val server: SeerrMediaServer,
    val packageName: String,
    val activityName: String,
) {
    Jellyfin(SeerrMediaServer.Jellyfin, "org.jellyfin.androidtv", "org.jellyfin.androidtv.ui.startup.StartupActivity"),
    Emby(SeerrMediaServer.Emby, "tv.emby.embyatv", "tv.emby.embyatv.startup.StartupActivity"),
    ;

    /** Opens [itemId] in the app, which shows it from whichever server the app is signed in to. */
    fun intentFor(itemId: String): Intent =
        Intent(Intent.ACTION_VIEW)
            .setClassName(packageName, activityName)
            .putExtra(MEDIA_SERVER_EXTRA_ITEM_ID, itemId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    companion object {
        fun forServer(server: SeerrMediaServer): TvMediaServerApp? = entries.firstOrNull { it.server == server }
    }
}

/**
 * Opens [item] in its media server's TV app, or null when there is no such app, it is not installed here, or the installed
 * one has no activity by that name. `getPackageInfo(String, Int)` is the only overload below API 33, and `minSdk` is 26.
 */
@Suppress("DEPRECATION")
internal fun Context.mediaServerHandOff(item: MediaServerItem?): (() -> Unit)? {
    val app = item?.let { TvMediaServerApp.forServer(it.server) } ?: return null
    if (attempt { packageManager.getPackageInfo(app.packageName, 0) }.isFailure) return null
    val intent = app.intentFor(item.itemId)
    if (intent.resolveActivity(packageManager) == null) return null
    return { attempt { startActivity(intent) } }
}
