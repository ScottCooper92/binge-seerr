package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.nav.BingeTvNavRail
import com.binge.designsystem.tv.nav.TvNavRailItem
import com.cooper.binge.companion.seerr.ui.tv.TvDestination
import com.cooper.binge.companion.seerr.ui.tv.TvShellScaffold
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The television's shell rail with a long account name and every destination, at font scale 1.3 only (the TV rule),
 * through the design system's `assertNoTextClipped`. The rail is checked as it rests, collapsed beside the content, and open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvShellClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test fun `the shell rail at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Shell() }

    @Test fun `the expanded rail at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { ExpandedRail() }
}

/**
 * The rail open, the state it takes when focus is on it, because the shell shows labels only then. The items are built
 * here from the app's destinations, since the shell's own mapping is private: a destination added there needs adding here.
 */
@Composable
private fun ExpandedRail() {
    @Composable
    fun item(destination: TvDestination) =
        TvNavRailItem(key = destination, label = stringResource(destination.labelRes), icon = destination.icon)
    BingeTvNavRail(
        header = item(TvDestination.Account).copy(displayName = LONG_ROW_NAME),
        items = listOf(item(TvDestination.Hub), item(TvDestination.Issues)),
        footer = item(TvDestination.Settings),
        selectedKey = TvDestination.Hub,
        onSelect = {},
        expanded = true,
        pinFooter = true,
    ) {
        Content()
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
    TvShellScaffold(
        selected = TvDestination.Hub,
        onSelect = {},
        accountName = LONG_ROW_NAME,
    ) {
        Content()
    }
}
