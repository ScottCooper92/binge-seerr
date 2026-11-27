package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.ui.requests.OpenTarget
import com.cooper.binge.companion.seerr.ui.requests.ServiceApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

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

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `play opens the title in the app where there is one`() {
        val app = OpenTarget.App(ServiceApp("Ephyra") { emptyList() }, Intent("test.OPEN_EPHYRA"))
        val opened = mutableListOf<String>()

        TitleWatch(app).play(context) { opened += it }

        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals("test.OPEN_EPHYRA", started?.action)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `play opens the server's web page where no app answers`() {
        val opened = mutableListOf<String>()

        TitleWatch(OpenTarget.Web("https://jf.example/web/#/details?id=abc")).play(context) { opened += it }

        assertEquals(listOf("https://jf.example/web/#/details?id=abc"), opened)
        assertNull(shadowOf(context as android.app.Application).nextStartedActivity)
    }
}
