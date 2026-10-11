package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.SettingsRoute
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenNamesTest {
    @Test
    fun `a screen is reported by its fixed name, never with the server's id`() {
        assertEquals("hub", HubRoute.screenName())
        assertEquals("settings", SettingsRoute.screenName())
        assertEquals("request_detail", RequestDetailRoute(requestId = 4812).screenName())
    }

    @Test
    fun `television destinations are marked apart from the phone's screens`() {
        val names = TvDestination.entries.map { it.screenName() }
        assertTrue(names.all { it.startsWith("tv_") })
        assertEquals(names.size, names.toSet().size)
    }
}
