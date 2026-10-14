package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val PLEX = 1
private const val JELLYFIN = 2
private const val EMBY = 3
private const val NOT_CONFIGURED = 4

/** A title reaches a media server's apps only where the server is one whose apps open an item by Seerr's `jellyfinMediaId`. */
class MediaServerItemTest {
    private fun profile(mediaServerType: Int?) =
        SeerrServerProfile(variant = SeerrVariant.Seerr, version = null, settings = SeerrPublicSettings(mediaServerType = mediaServerType))

    @Test
    fun `a Jellyfin or Emby server's title is its item on that server`() {
        assertEquals(MediaServerItem(SeerrMediaServer.Jellyfin, "a1b2"), profile(JELLYFIN).mediaServerItem("a1b2"))
        assertEquals(MediaServerItem(SeerrMediaServer.Emby, "77"), profile(EMBY).mediaServerItem("77"))
    }

    @Test
    fun `a title with no id, or a blank one, has no item`() {
        assertNull(profile(JELLYFIN).mediaServerItem(null))
        assertNull(profile(JELLYFIN).mediaServerItem(" "))
    }

    @Test
    fun `a Plex, unconfigured or unknown server has no item an app can be handed`() {
        assertNull(profile(PLEX).mediaServerItem("a1b2"))
        assertNull(profile(NOT_CONFIGURED).mediaServerItem("a1b2"))
        assertNull(profile(null).mediaServerItem("a1b2"))
    }
}
