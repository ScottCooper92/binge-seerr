package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.binge.designsystem.testing.assertNoTextClipped
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistRow
import com.cooper.binge.companion.seerr.ui.issues.IssueRow
import com.cooper.binge.companion.seerr.ui.requests.RequestRow
import com.cooper.binge.companion.seerr.ui.users.UserRow
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NOW_MILLIS = 1_770_000_000_000L

/**
 * The app's list rows with every optional line filled and long text, at font scales 1.3 and 2.0, each through the
 * design system's `assertNoTextClipped`. The request row clipped its requester line at 1.3 until #1477; this is the
 * guard against the next one. Native graphics, because legacy graphics measures text at a pixel a character and
 * nothing wraps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h8000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        fontScale: Float,
        row: @Composable () -> Unit,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                BingeExpressiveTheme { Box(Modifier.width(PHONE_WIDTH)) { row() } }
            }
        }
        rule.waitForIdle()
        val checked = rule.assertNoTextClipped()
        // A pass that checked nothing is no pass.
        assertTrue("only $checked text nodes were checked", checked >= MIN_CHECKED)
    }

    @Test fun `a request row at 1_3`() = check(FONT_13) { RequestRow(worstCaseRequest(), onClick = {}, onManage = {}, now = NOW_MILLIS) }

    @Test fun `a request row at 2_0`() = check(FONT_20) { RequestRow(worstCaseRequest(), onClick = {}, onManage = {}, now = NOW_MILLIS) }

    @Test fun `an issue row at 1_3`() = check(FONT_13) { IssueRow(worstCaseIssue(), onClick = {}, now = NOW_MILLIS) }

    @Test fun `an issue row at 2_0`() = check(FONT_20) { IssueRow(worstCaseIssue(), onClick = {}, now = NOW_MILLIS) }

    @Test fun `a blocklist row at 1_3`() =
        check(FONT_13) {
            BlocklistRow(worstCaseBlocklistItem(), isActing = false, onClick = {}, onRemove = {})
        }

    @Test fun `a blocklist row at 2_0`() =
        check(FONT_20) {
            BlocklistRow(worstCaseBlocklistItem(), isActing = false, onClick = {}, onRemove = {})
        }

    @Test fun `a user row at 1_3`() =
        check(FONT_13) {
            UserRow(worstCaseUser(), selecting = false, selected = false, onClick = {}, onLongClick = {})
        }

    @Test fun `a user row at 2_0`() =
        check(FONT_20) {
            UserRow(worstCaseUser(), selecting = false, selected = false, onClick = {}, onLongClick = {})
        }

    private companion object {
        val PHONE_WIDTH = 412.dp
        const val MIN_CHECKED = 3
        const val FONT_13 = 1.3f
        const val FONT_20 = 2.0f
    }
}
