package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** A list under a pinned line keeps only the bottom: the top went above the line, and the sides stay outside (#1054). */
class ScreenInsetsTest {
    @Test
    fun `below a pinned line, only the bottom is left`() {
        val padding = PaddingValues(start = 4.dp, top = 10.dp, end = 6.dp, bottom = 20.dp).belowPinnedLine()

        assertEquals(0.dp, padding.calculateTopPadding())
        assertEquals(20.dp, padding.calculateBottomPadding())
        assertEquals(0.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(0.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
    }
}
