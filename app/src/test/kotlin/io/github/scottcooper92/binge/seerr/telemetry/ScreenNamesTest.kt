package io.github.scottcooper92.binge.seerr.telemetry

import io.github.scottcooper92.binge.seerr.ui.HubRoute
import io.github.scottcooper92.binge.seerr.ui.RequestDetailRoute
import io.github.scottcooper92.binge.seerr.ui.SettingsRoute
import io.github.scottcooper92.binge.seerr.ui.tv.TvDestination
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
