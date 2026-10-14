package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import com.cooper.binge.companion.seerr.ui.tv.TvHomeShell
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The television's Home with its top row of buttons, at font scale 1.3 only (the TV rule), through the design system's
 * `assertNoTextClipped`. The buttons are checked as they rest, icon only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvShellClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test fun `Home with its top row at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Shell() }
}

@Composable
private fun Content() {
    Box(Modifier.fillMaxSize()) {
        Text("Hub content for the clipping guard")
        Text("A second line of content so that the guard has enough text to check")
        Text("A third line of content")
    }
}

@Composable
private fun Shell() {
    TvHomeShell(page = TvDestination.Hub, onOpenPage = {}, onClosePage = {}) { _, _ ->
        Content()
    }
}
