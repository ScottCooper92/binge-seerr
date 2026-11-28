package com.cooper.binge.companion.seerr.ui.settings.server

import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.homelab.core.telemetry.Analytics

/** One committed change to a server settings page, named by the page. */
fun Analytics.serverSettingChanged(
    page: String,
    action: String = "saved",
) {
    event(AnalyticsEvents.SERVER_SETTINGS_CHANGED, mapOf(AnalyticsEvents.PARAM_ACTION to action, AnalyticsEvents.PARAM_TYPE to page))
}

/** One committed change to a media server's connection, its libraries or its scan, named by what changed and the server kind. */
fun Analytics.mediaServerChanged(
    kind: MediaServerKind,
    action: String,
) {
    event(
        AnalyticsEvents.MEDIA_SERVER_CHANGED,
        mapOf(AnalyticsEvents.PARAM_ACTION to action, AnalyticsEvents.PARAM_ORIGIN to kind.name.lowercase()),
    )
}
