package com.cooper.binge.companion.seerr.ui.users.settings

import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.homelab.core.telemetry.Analytics

/** One committed change to a user's own settings page, named by the page. */
fun Analytics.userSettingChanged(page: String) {
    event(AnalyticsEvents.USER_CHANGED, mapOf(AnalyticsEvents.PARAM_ACTION to "settings", AnalyticsEvents.PARAM_TYPE to page))
}
