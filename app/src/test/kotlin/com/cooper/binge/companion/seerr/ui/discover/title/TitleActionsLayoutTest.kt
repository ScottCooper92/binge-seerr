package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w360dp-h640dp-xhdpi"
private const val TABLET = "w840dp-h900dp-xhdpi"

/**
 * The action row at a narrow phone and the largest font: nothing is clipped past the window, and the primary shares its row with
 * none or one round button and sits above the round buttons from two. A wide window keeps one row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleActionsLayoutTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(
        trailer: Boolean,
        report: Boolean,
        fontScale: Float = 2f,
    ) = rule.setContent {
        SeerrTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                TitleActions(
                    model =
                        titleActionsModel(
                            VersionState(TitleAvailability.PartiallyAvailable, mayRequest = false),
                            null,
                            emptyList(),
                            report,
                        ),
                    onPrimary = {},
                    onTrailer = if (trailer) ({}) else null,
                    onReport = if (report) ({}) else null,
                )
            }
        }
    }

    private fun bounds(text: String): DpRect = rule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertWithinWindow(vararg labels: String) {
        val window = rule.onRoot().getUnclippedBoundsInRoot()
        labels.forEach { label ->
            val box = bounds(label)
            assertTrue("$label starts inside the window", box.left >= window.left)
            assertTrue("$label ends inside the window", box.right <= window.right)
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the primary alone has the whole row and its label stays inside the window`() {
        show(trailer = false, report = false)

        assertWithinWindow("Partial")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `one round button shares the row with the primary and neither is clipped`() {
        show(trailer = true, report = false)

        assertWithinWindow("Partial", "Trailer")
        assertTrue("the trailer is beside the primary, not below it", bounds("Trailer").left >= bounds("Partial").right)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `two round buttons sit on a row below the primary and neither is clipped`() {
        show(trailer = true, report = true)

        assertWithinWindow("Partial", "Trailer", "Report")
        assertTrue("the buttons are below the primary", bounds("Trailer").top >= bounds("Partial").bottom)
        assertEquals("the buttons share a row", bounds("Trailer").top, bounds("Report").top)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `two round buttons at font scale 1_3 sit below the primary too`() {
        show(trailer = true, report = true, fontScale = 1.3f)

        assertWithinWindow("Partial", "Trailer", "Report")
        assertTrue(bounds("Trailer").top >= bounds("Partial").bottom)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `a wide window keeps one row for the primary and both buttons`() {
        show(trailer = true, report = true, fontScale = 1f)

        assertTrue("the buttons stay beside the primary", bounds("Trailer").left >= bounds("Partial").right)
        assertEquals(bounds("Trailer").top, bounds("Report").top)
    }
}
