package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.ui.settings.server.MediaServerKind
import com.cooper.binge.companion.seerr.ui.settings.server.mediaServerChanged
import com.cooper.binge.companion.seerr.ui.settings.server.serverSettingChanged
import com.cooper.binge.companion.seerr.ui.users.settings.userSettingChanged
import com.cooper.homelab.core.testing.RecordingAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test

class GapEventsTest {
    @Test
    fun `a user's settings save names the page it changed`() {
        val analytics = RecordingAnalytics()

        analytics.userSettingChanged("general")

        assertEquals(
            listOf(
                AnalyticsEvents.USER_CHANGED to mapOf(AnalyticsEvents.PARAM_ACTION to "settings", AnalyticsEvents.PARAM_TYPE to "general"),
            ),
            analytics.events,
        )
    }

    @Test
    fun `a server settings save names its page, and a regenerated key says so`() {
        val analytics = RecordingAnalytics()

        analytics.serverSettingChanged("network")
        analytics.serverSettingChanged("api_key", action = "regenerated")

        assertEquals(
            listOf(
                AnalyticsEvents.SERVER_SETTINGS_CHANGED to
                    mapOf(AnalyticsEvents.PARAM_ACTION to "saved", AnalyticsEvents.PARAM_TYPE to "network"),
                AnalyticsEvents.SERVER_SETTINGS_CHANGED to
                    mapOf(AnalyticsEvents.PARAM_ACTION to "regenerated", AnalyticsEvents.PARAM_TYPE to "api_key"),
            ),
            analytics.events,
        )
    }

    @Test
    fun `a media server change names the server kind in lower case, never an address`() {
        val analytics = RecordingAnalytics()

        analytics.mediaServerChanged(MediaServerKind.Emby, "scan_started")

        assertEquals(
            listOf(
                AnalyticsEvents.MEDIA_SERVER_CHANGED to
                    mapOf(AnalyticsEvents.PARAM_ACTION to "scan_started", AnalyticsEvents.PARAM_ORIGIN to "emby"),
            ),
            analytics.events,
        )
    }
}
