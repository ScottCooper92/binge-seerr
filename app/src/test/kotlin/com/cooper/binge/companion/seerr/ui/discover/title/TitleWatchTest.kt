package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.flags.WatchPlayer
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

    private val ephyra = OpenTarget.App(ServiceApp("Ephyra") { emptyList() }, Intent("test.OPEN_EPHYRA"))

    /** What opened, in order: "ephyra", or the label of a row that was clicked, or "chooser". */
    private fun play(
        player: WatchPlayer,
        withEphyra: Boolean = true,
        rows: List<WatchPlayer> = listOf(WatchPlayer.Binge, WatchPlayer.MediaServer),
    ): String? {
        val opened = mutableListOf<String>()
        val watch =
            TitleWatch(
                ephyra = ephyra.takeIf { withEphyra },
                options = rows.map { way -> WatchRow(way, way.name) { opened += way.name } },
            )
        watch.play(context, player) { opened += "chooser" }
        shadowOf(context as android.app.Application).nextStartedActivity?.let { opened += "ephyra" }
        return opened.singleOrNull()
    }

    @Test
    fun `automatic opens Ephyra where it answers`() = assertEquals("ephyra", play(WatchPlayer.Automatic))

    @Test
    fun `automatic with no Ephyra and two ways offers the chooser`() =
        assertEquals("chooser", play(WatchPlayer.Automatic, withEphyra = false))

    @Test
    fun `automatic with no Ephyra and one way opens it`() =
        assertEquals("MediaServer", play(WatchPlayer.Automatic, withEphyra = false, rows = listOf(WatchPlayer.MediaServer)))

    @Test
    fun `ask offers the chooser even where Ephyra answers`() = assertEquals("chooser", play(WatchPlayer.Ask))

    @Test
    fun `a named way opens at once`() {
        assertEquals("Binge", play(WatchPlayer.Binge))
        assertEquals("MediaServer", play(WatchPlayer.MediaServer))
        assertEquals("ephyra", play(WatchPlayer.Ephyra))
    }

    @Test
    fun `a named way that does not answer falls back to what Watch always did`() {
        // Ephyra uninstalled: two ways left, so the chooser.
        assertEquals("chooser", play(WatchPlayer.Ephyra, withEphyra = false))
        // Binge does not answer the link: Ephyra answers.
        assertEquals("ephyra", play(WatchPlayer.Binge, rows = listOf(WatchPlayer.MediaServer)))
    }

    @Test
    fun `nothing to watch it with is the chooser, which says why`() =
        assertEquals("chooser", play(WatchPlayer.Ephyra, withEphyra = false, rows = emptyList()))

    @Test
    fun `the chooser lists Ephyra first, then the rest`() {
        val watch = TitleWatch(ephyra, listOf(WatchRow(WatchPlayer.Binge, "b") {}))
        assertEquals(listOf(WatchPlayer.Ephyra, WatchPlayer.Binge), watch.ways(context).map { it.way })
    }
}
