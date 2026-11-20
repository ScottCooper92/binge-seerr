package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile

/** The media server the Seerr server fronts, by name; null where it has none configured or one this app does not know. */
internal fun SeerrServerProfile.mediaServerName(): String? =
    when (mediaServer) {
        SeerrMediaServer.Plex -> "Plex"
        SeerrMediaServer.Jellyfin -> "Jellyfin"
        SeerrMediaServer.Emby -> "Emby"
        SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> null
    }
