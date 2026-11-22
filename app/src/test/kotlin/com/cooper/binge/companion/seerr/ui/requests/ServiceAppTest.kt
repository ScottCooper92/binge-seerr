package com.cooper.binge.companion.seerr.ui.requests

import android.content.Intent
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which button a section gets: its app when one answers, the web link otherwise, nothing with neither. */
@RunWith(RobolectricTestRunner::class)
class ServiceAppTest {
    private val web = "https://jellyfin.example.com/web/#/details?id=1"
    private val release = serviceApps(debug = false)
    private val debug = serviceApps(debug = true)
    private val item =
        ServiceItem(
            serverUrl = "https://jellyfin.example.com",
            itemId = "0123456789abcdef0123456789abcdef",
            tmdbType = "movie",
            tmdbId = 603,
        )

    @Test
    fun `the intent carries the action, the package and all four extras`() {
        val intent = ephyraOpenItem("com.cooper.homelab.ephyra", item)

        assertEquals("com.cooper.homelab.ephyra.action.OPEN_ITEM", intent.action)
        assertEquals("com.cooper.homelab.ephyra", intent.`package`)
        assertTrue(intent.hasCategory(Intent.CATEGORY_DEFAULT))
        assertEquals("https://jellyfin.example.com", intent.getStringExtra("com.cooper.homelab.ephyra.extra.SERVER_URL"))
        assertEquals("0123456789abcdef0123456789abcdef", intent.getStringExtra("com.cooper.homelab.ephyra.extra.ITEM_ID"))
        assertEquals("movie", intent.getStringExtra("com.cooper.homelab.ephyra.extra.TMDB_TYPE"))
        assertEquals(603, intent.getIntExtra("com.cooper.homelab.ephyra.extra.TMDB_ID", -1))
    }

    @Test
    fun `an extra with no value is left off`() {
        val intent = ephyraOpenItem("com.cooper.homelab.ephyra", ServiceItem(tmdbType = "tv", tmdbId = 7))

        assertTrue(!intent.hasExtra("com.cooper.homelab.ephyra.extra.SERVER_URL"))
        assertTrue(!intent.hasExtra("com.cooper.homelab.ephyra.extra.ITEM_ID"))
        assertEquals(7, intent.getIntExtra("com.cooper.homelab.ephyra.extra.TMDB_ID", -1))
    }

    @Test
    fun `an app that answers the intent wins over the web link`() {
        val target = ServiceKind.Jellyfin.openTarget(item, web, release) { it.`package` == "com.cooper.homelab.ephyra" }

        assertEquals("Ephyra", (target as OpenTarget.App).app.name)
        assertEquals("com.cooper.homelab.ephyra", target.intent.`package`)
    }

    @Test
    fun `nothing that answers falls back to the web link`() {
        assertEquals(OpenTarget.Web(web), ServiceKind.Jellyfin.openTarget(item, web, release) { false })
    }

    @Test
    fun `no app and no link gives no button`() {
        assertNull(ServiceKind.Jellyfin.openTarget(item, null, release) { false })
    }

    @Test
    fun `an address a browser cannot open is no link`() {
        assertNull(ServiceKind.Jellyfin.openTarget(item, "not a url", release) { false })
    }

    @Test
    fun `a debug build also tries the debug package`() {
        val target = ServiceKind.Jellyfin.openTarget(item, web, debug) { it.`package` == "com.cooper.homelab.ephyra.debug" }

        assertEquals("com.cooper.homelab.ephyra.debug", (target as OpenTarget.App).intent.`package`)
    }

    @Test
    fun `a release build does not try the debug package`() {
        val tried = mutableListOf<String?>()
        ServiceKind.Jellyfin.openTarget(item, web, release) {
            tried += it.`package`
            false
        }

        assertEquals(listOf("com.cooper.homelab.ephyra"), tried)
    }

    @Test
    fun `a service with no app of its own always opens the web link`() {
        assertEquals(OpenTarget.Web(web), ServiceKind.Radarr.openTarget(item, web, release) { true })
        assertEquals(OpenTarget.Web(web), ServiceKind.Plex.openTarget(item, web, release) { true })
    }

    @Test
    fun `only a Jellyfin server offers Ephyra even where Ephyra answers`() {
        val answers = { intent: Intent -> intent.`package` == "com.cooper.homelab.ephyra" }

        assertEquals(
            "Ephyra",
            (SeerrMediaServer.Jellyfin.toServiceKind()?.openTarget(item, web, release, answers) as OpenTarget.App).app.name,
        )
        assertEquals(OpenTarget.Web(web), SeerrMediaServer.Plex.toServiceKind()?.openTarget(item, web, release, answers))
        assertEquals(OpenTarget.Web(web), SeerrMediaServer.Emby.toServiceKind()?.openTarget(item, web, release, answers))
        assertNull(SeerrMediaServer.NotConfigured.toServiceKind())
        assertNull(SeerrMediaServer.Unknown.toServiceKind())
    }

    @Test
    fun `the base address is what comes before the web client`() {
        assertEquals("https://jellyfin.example.com", mediaServerBaseUrl(web))
        assertEquals(
            "https://host.example.com/jellyfin",
            mediaServerBaseUrl("https://host.example.com/jellyfin/web/index.html#/details?id=1"),
        )
        assertEquals("http://192.168.1.5:8096", mediaServerBaseUrl("http://192.168.1.5:8096/details?id=1"))
        assertNull(mediaServerBaseUrl(null))
        assertNull(mediaServerBaseUrl("not a url"))
    }
}
