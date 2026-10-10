package io.github.scottcooper92.binge.seerr.ui.tv

import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A board with no pager yet reads as loading and empty, never as an empty list (#1054). */
@RunWith(RobolectricTestRunner::class)
class TvPagedRowsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `no pager is still loading, with nothing to show`() {
        var rows: TvPagedRows<String>? = null
        rule.setContent { rows = null.toRows<String>(lastRefresh = null) { it } }
        rule.waitForIdle()

        val read = rows!!
        assertEquals(0, read.count)
        assertNull(read.at(0))
        assertEquals(TvLoadPhase.Loading, read.refresh)
        assertEquals(TvLoadPhase.Idle, read.append)
    }
}
