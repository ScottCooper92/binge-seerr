package io.github.scottcooper92.binge.seerr.ui

import androidx.test.core.app.ApplicationProvider
import io.github.scottcooper92.binge.seerr.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * That the meta separator keeps the spaces it is declared with (#247).
 *
 * AAPT trims leading and trailing whitespace from a string resource unless the value is quoted, and
 * nothing else notices: the resource compiles, every `joinToString` using it still runs, and the app
 * renders `4.3 GB·About 18 min left` across 29 call sites. Only a committed frame caught it, and only
 * on the one screen that has frames. This asserts the bytes instead, so the trap cannot return
 * silently to a surface no baseline covers.
 */
@RunWith(RobolectricTestRunner::class)
class MetaSeparatorTest {
    @Test
    fun `the meta separator survives aapt with a space either side`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(" · ", context.getString(R.string.hub_meta_separator))
    }

    /**
     * The joined shape the call sites actually build — the separator alone could be right while a
     * caller trimmed around it.
     */
    @Test
    fun `a joined meta line reads with spaces around the glyph`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val line = listOf("4.3 GB", "About 18 min left")
            .joinToString(context.getString(R.string.hub_meta_separator))
        assertEquals("4.3 GB · About 18 min left", line)
    }
}
