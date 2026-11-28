package com.cooper.binge.companion.seerr.ui.settings

import com.cooper.binge.companion.seerr.feature.admin.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer

fun SeerrMediaServer.labelRes(): Int =
    when (this) {
        SeerrMediaServer.Jellyfin -> R.string.user_origin_jellyfin
        SeerrMediaServer.Emby -> R.string.user_origin_emby
        SeerrMediaServer.Plex -> R.string.user_origin_plex
        SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> R.string.server_settings_media_server
    }
