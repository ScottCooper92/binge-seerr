package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `ListRow` sizes its card from the intrinsic height of its content, so a wrapping row has to count
 * every line it will draw. `FlowRow` did not, and the line below it was clipped.
 */
@RunWith(RobolectricTestRunner::class)
class WrappingRowTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `an intrinsic-height parent sizes to every wrapped line`() {
        var size = IntSize.Zero
        composeTestRule.setContent {
            Row(Modifier.width(100.dp).height(IntrinsicSize.Min).onSizeChanged { size = it }) {
                WrappingRow(horizontalSpacing = 4.dp, verticalSpacing = 4.dp, modifier = Modifier.fillMaxHeight()) {
                    repeat(3) { Box(Modifier.size(width = 60.dp, height = 10.dp)) }
                }
            }
        }
        composeTestRule.waitForIdle()
        // Three 60dp chips in 100dp wrap to one per line: three lines of 10dp and two 4dp gaps.
        assertEquals(with(composeTestRule.density) { 38.dp.roundToPx() }, size.height)
    }

    @Test
    fun `children that fit share a line`() {
        var size = IntSize.Zero
        composeTestRule.setContent {
            Row(Modifier.width(100.dp).height(IntrinsicSize.Min).onSizeChanged { size = it }) {
                WrappingRow(horizontalSpacing = 4.dp, verticalSpacing = 4.dp, modifier = Modifier.fillMaxHeight()) {
                    repeat(2) { Box(Modifier.size(width = 30.dp, height = 10.dp)) }
                }
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(with(composeTestRule.density) { 10.dp.roundToPx() }, size.height)
    }
}
