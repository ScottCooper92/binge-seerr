package com.cooper.binge.companion.seerr.ui.requests

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which button a section gets: its app when installed, the web link otherwise, nothing with neither. */
class ServiceAppTest {
    private val web = "https://jellyfin.example.com/web/#/details?id=1"
    private val release = serviceApps(debug = false)
    private val debug = serviceApps(debug = true)

    @Test
    fun `the installed app wins over the web link`() {
        val target = ServiceKind.Jellyfin.openTarget(web, release) { it == "com.cooper.homelab.ephyra" }

        assertEquals(OpenTarget.App(release.getValue(ServiceKind.Jellyfin), "com.cooper.homelab.ephyra"), target)
    }

    @Test
    fun `a missing app falls back to the web link`() {
        assertEquals(OpenTarget.Web(web), ServiceKind.Jellyfin.openTarget(web, release) { false })
    }

    @Test
    fun `no app and no link gives no button`() {
        assertNull(ServiceKind.Jellyfin.openTarget(null, release) { false })
    }

    @Test
    fun `an address a browser cannot open is no link`() {
        assertNull(ServiceKind.Jellyfin.openTarget("not a url", release) { false })
    }

    @Test
    fun `a debug build also finds the debug package`() {
        val target = ServiceKind.Jellyfin.openTarget(web, debug) { it == "com.cooper.homelab.ephyra.debug" }

        assertEquals("com.cooper.homelab.ephyra.debug", (target as OpenTarget.App).packageName)
    }

    @Test
    fun `a release build does not look for the debug package`() {
        assertEquals(OpenTarget.Web(web), ServiceKind.Jellyfin.openTarget(web, release) { it.endsWith(".debug") })
    }

    @Test
    fun `a service with no app of its own always opens the web link`() {
        assertEquals(OpenTarget.Web(web), ServiceKind.Radarr.openTarget(web, release) { true })
        assertEquals(OpenTarget.Web(web), ServiceKind.Plex.openTarget(web, release) { true })
    }
}
