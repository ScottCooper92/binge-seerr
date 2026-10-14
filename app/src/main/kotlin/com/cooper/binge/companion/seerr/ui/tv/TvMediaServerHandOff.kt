package com.cooper.binge.companion.seerr.ui.tv

private const val JELLYFIN_TV_PACKAGE = "org.jellyfin.androidtv"

/** PROTOTYPE: whether Jellyfin's Android TV app is installed, per the manifest's `queries`. */
internal fun android.content.Context.jellyfinTvInstalled(): Boolean =
    packageManager.getLaunchIntentForPackage(JELLYFIN_TV_PACKAGE) != null

/**
 * PROTOTYPE: opens [itemId] on Jellyfin's TV app's own details page. The app's StartupActivity takes the item as its
 * `ItemId` extra (jellyfin-androidtv, `StartupActivity.EXTRA_ITEM_ID`); it shows it from whichever server it is signed in to.
 */
internal fun android.content.Context.openInJellyfinTv(itemId: String) {
    val intent =
        android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setClassName(JELLYFIN_TV_PACKAGE, "$JELLYFIN_TV_PACKAGE.ui.startup.StartupActivity")
            .putExtra("ItemId", itemId)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    com.binge.companion.sdk.attempt { startActivity(intent) }
}
