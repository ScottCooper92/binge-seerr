package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.binge.designsystem.testing.assertNoTextClipped
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.theme.SeerrTvTheme
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import com.cooper.binge.companion.seerr.ui.tv.TvHomeShell
import com.cooper.binge.companion.seerr.ui.tv.TvTopBar
import com.cooper.binge.companion.seerr.ui.tv.TvTopBarAccount
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertTrue
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
            settle = { onNode(hasContentDescription(string(R.string.tv_home))).requestFocus() },
        ) { Shell() }
}

/**
 * The bar alone at its fullest, for the design system's trailing slot (#1925): both tabs, the demo tag, the avatar with a
 * long name and the gear, at 1x, 1.3x and 2x. The app has two tabs at most, so this is its widest bar. 2x is past the TV
 * rule's 1.3, deliberately: the shared bar's own notes say it holds up to 1.3 beside the trailing slot, and this pins what
 * this app's two tabs do beyond that, so [assertNoTvClippingAt] (which refuses 2x) is not used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvTopBarFitClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    private fun check(fontScale: Float) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                SeerrTvTheme { FullBar() }
            }
        }
        rule.waitForIdle()
        // Focused, so the tabs are at full size: at rest they are drawn at 0.8, which the check would read as clipping.
        rule.onNode(hasContentDescription(string(R.string.tv_home))).requestFocus()
        rule.waitForIdle()
        val checked = rule.assertNoTextClipped()
        assertTrue("only $checked text nodes were checked", checked >= BAR_TEXT_NODES)
    }

    @Test fun `the fullest bar at 1x`() = check(1f)

    @Test fun `the fullest bar at 1_3`() = check(FONT_SCALE_13)

    @Test fun `the fullest bar at 2_0`() = check(FONT_SCALE_20)
}

private const val BAR_TEXT_NODES = 3

@Composable
private fun FullBar() {
    val tabs = listOf(TvDestination.Hub, TvDestination.Issues)
    val opener = remember { TvDestination.entries.associateWith { FocusRequester() } }
    val entry = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .focusRequester(entry)
                .focusable(),
        )
        TvTopBar(
            destinations = listOf(TvDestination.Account) + tabs + TvDestination.Settings,
            current = TvDestination.Hub,
            opener = opener,
            down = entry,
            visible = true,
            account = TvTopBarAccount(name = LONG_ROW_NAME, avatarUrl = null, demo = true),
            onFocusedChange = {},
            onSelect = {},
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
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
    TvHomeShell(tab = TvDestination.Hub, sheet = null, onSelectTab = {}, onOpenSheet = {}, onCloseSheet = {}) { _, _, _ ->
        Content()
    }
}
