package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.requestFocus
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import com.cooper.binge.companion.seerr.ui.tv.TvHomeShell
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The television's Home under its top bar, at font scale 1.3 only (the TV rule), through the design system's
 * `assertNoTextClipped`. The bar is checked holding focus, at full size: at rest its tabs are drawn scaled down, which the
 * check would read as clipping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvShellClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test
    fun `Home with its top row at 1_3`() =
        rule.assertNoTvClippingAt(
            FONT_SCALE_13,
            settle = { onNode(hasContentDescription(string(R.string.tv_rail_hub))).requestFocus() },
        ) { Shell() }
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
    TvHomeShell(tab = TvDestination.Hub, sheet = null, onSelectTab = {}, onOpenSheet = {}, onCloseSheet = {}) { _, _ ->
        Content()
    }
}
