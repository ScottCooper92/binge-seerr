package com.cooper.binge.companion.seerr.ui.tv

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.ui.MediaServerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * A title opens in its media server's TV app by an explicit intent to the app's startup activity, carrying the item's id. The
 * hand-off is offered only when that app is installed, so a press never lands on nothing.
 */
@RunWith(RobolectricTestRunner::class)
class TvMediaServerAppTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the intent names the app's startup activity and carries the item's id`() {
        val intent = TvMediaServerApp.Jellyfin.intentFor("a1b2")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(ComponentName("org.jellyfin.androidtv", "org.jellyfin.androidtv.ui.startup.StartupActivity"), intent.component)
        assertEquals("a1b2", intent.getStringExtra(MEDIA_SERVER_EXTRA_ITEM_ID))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `each server's app is its own, and Plex has none`() {
        assertEquals(TvMediaServerApp.Jellyfin, TvMediaServerApp.forServer(SeerrMediaServer.Jellyfin))
        assertEquals(TvMediaServerApp.Emby, TvMediaServerApp.forServer(SeerrMediaServer.Emby))
        assertNull(TvMediaServerApp.forServer(SeerrMediaServer.Plex))
    }

    @Test
    fun `with the app not installed there is no hand-off`() {
        assertNull(context.mediaServerHandOff(MediaServerItem(SeerrMediaServer.Jellyfin, "a1b2")))
    }

    @Test
    fun `with no item there is no hand-off`() {
        install(TvMediaServerApp.Jellyfin)

        assertNull(context.mediaServerHandOff(null))
    }

    @Test
    fun `with the app installed the hand-off opens the item in it`() {
        install(TvMediaServerApp.Emby)

        val handOff = context.mediaServerHandOff(MediaServerItem(SeerrMediaServer.Emby, "77"))
        assertNotNull(handOff)
        handOff?.invoke()

        val started = shadowOf(context).nextStartedActivity
        assertEquals(ComponentName("tv.emby.embyatv", "tv.emby.embyatv.startup.StartupActivity"), started.component)
        assertEquals("77", started.getStringExtra(MEDIA_SERVER_EXTRA_ITEM_ID))
    }

    /** One app installed is not another's: Jellyfin's being here offers nothing for an Emby server. */
    @Test
    fun `another server's app being installed offers nothing`() {
        install(TvMediaServerApp.Jellyfin)

        assertNull(context.mediaServerHandOff(MediaServerItem(SeerrMediaServer.Emby, "77")))
    }

    private fun install(app: TvMediaServerApp) {
        val packages = shadowOf(context.packageManager)
        packages.installPackage(PackageInfo().apply { packageName = app.packageName })
        packages.addActivityIfNotPresent(ComponentName(app.packageName, app.activityName))
    }
}
