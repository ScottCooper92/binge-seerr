package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.binge.designsystem.testing.assertNoTextClipped
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import org.junit.Assert.assertTrue

internal const val FONT_13 = 1.3f
internal const val FONT_20 = 2.0f
internal val PHONE_WIDTH = 412.dp
private const val MIN_CHECKED = 3

/**
 * Renders [content] in the app's theme, [width] wide, at [fontScale], and fails if any of its text is clipped. The
 * viewport of these tests is tall, so a page that scrolls is in view whole: text that is only scrolled out is skipped
 * by the assertion. Also fails a pass that checked next to nothing, which is no pass.
 */
internal fun ComposeContentTestRule.assertClipFree(
    fontScale: Float,
    width: Dp = PHONE_WIDTH,
    content: @Composable () -> Unit,
) {
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            SeerrTheme { Box(Modifier.width(width)) { content() } }
        }
    }
    waitForIdle()
    val checked = assertNoTextClipped()
    assertTrue("only $checked text nodes were checked", checked >= MIN_CHECKED)
}
