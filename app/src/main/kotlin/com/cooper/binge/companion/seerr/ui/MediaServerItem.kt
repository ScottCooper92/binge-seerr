package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile

/** A title as the media server knows it: which server, and the id its own apps open the title by. */
data class MediaServerItem(
    val server: SeerrMediaServer,
    val itemId: String,
)

/**
 * The title [itemId] names on the media server this Seerr server fronts. Jellyfin and Emby both report it as Seerr's
 * `jellyfinMediaId`. Plex has none an app can be handed here, and an unknown or unconfigured server has no app to open,
 * so both are null.
 */
internal fun SeerrServerProfile.mediaServerItem(itemId: String?): MediaServerItem? {
    val id = itemId?.takeIf { it.isNotBlank() } ?: return null
    return when (mediaServer) {
        SeerrMediaServer.Jellyfin, SeerrMediaServer.Emby -> MediaServerItem(mediaServer, id)
        SeerrMediaServer.Plex, SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> null
    }
}
