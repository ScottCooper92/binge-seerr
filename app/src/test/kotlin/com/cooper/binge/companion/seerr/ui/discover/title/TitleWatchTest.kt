package com.cooper.binge.companion.seerr.ui.discover.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The item id Ephyra is given from the media server's web link. */
@RunWith(RobolectricTestRunner::class)
class TitleWatchTest {
    @Test
    fun `a Jellyfin web link names its item`() {
        assertEquals("abc123", itemIdOf("https://jf.example/web/index.html#!/details?id=abc123&serverId=s1"))
    }

    @Test
    fun `a newer Jellyfin link names it too`() {
        assertEquals("abc123", itemIdOf("https://jf.example/web/#/details?id=abc123"))
    }

    @Test
    fun `a link with no id names nothing`() {
        assertNull(itemIdOf("https://app.plex.tv/desktop#!/server/x/details?key=%2Flibrary%2Fmetadata%2F1"))
    }
}
