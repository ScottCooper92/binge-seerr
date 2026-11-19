package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.LicencesRoute
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.SettingsRoute
import com.cooper.binge.companion.seerr.ui.ThisAppRoute
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import com.cooper.binge.companion.seerr.util.sampleRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenNamesTest {
    @Test
    fun `a screen is reported by its fixed name, never with the server's id`() {
        assertEquals("hub", HubRoute.screenName())
        assertEquals("settings", SettingsRoute.screenName())
        assertEquals("licences", LicencesRoute.screenName())
        assertEquals("this_app", ThisAppRoute().screenName())
        assertEquals("request_detail", RequestDetailRoute(requestId = 4812).screenName())
    }

    /**
     * `screenName()` ends in a fallback that throws, and the phone's activity calls it for every route that reaches the top
     * of the stack, so a route added without a branch builds green and crashes on arrival (#1968, #1969). This builds one of
     * every route, from its constructor with sample values, and asks for its name.
     */
    @Test
    fun `every route has a screen name, so a new one cannot crash on arrival`() {
        val routes =
            SeerrRoute::class.java.permittedSubclasses
                .orEmpty()
                .toList()
        assertTrue("SeerrRoute's subclasses were not found", routes.size > 10)
        routes.forEach { route ->
            val name = (sampleRoute(route)).screenName()
            assertFalse("${route.simpleName} has a blank name", name.isBlank())
        }
    }

    @Test
    fun `television destinations are marked apart from the phone's screens`() {
        val names = TvDestination.entries.map { it.screenName() }
        assertTrue(names.all { it.startsWith("tv_") })
        assertEquals(names.size, names.toSet().size)
    }
}
