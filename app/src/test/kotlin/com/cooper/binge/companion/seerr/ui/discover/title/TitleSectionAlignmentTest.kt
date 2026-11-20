package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.binge.designsystem.component.DetailInfoEntry
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.binge.designsystem.R as DesR

/**
 * A section's heading starts where its content starts, on a phone and on a wide window, where the screen's content inset widens and a
 * heading that kept to its own padding sat out of line with the chips and the facts under it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleSectionAlignmentTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun start(text: String) =
        rule
            .onNodeWithText(text)
            .getUnclippedBoundsInRoot()
            .left.value

    /** A tag's visible edge: its label sits inside the compact tag's own horizontal padding. */
    private fun tagStart(text: String): Float {
        val padding = resources.getDimension(DesR.dimen.tag_compact_padding_h) / resources.displayMetrics.density
        return start(text) - padding
    }

    private fun check(besideHub: Boolean = false) {
        val keywords = resources.getString(R.string.title_page_keywords)
        val details = resources.getString(R.string.title_page_details)
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalIsSinglePaneNav provides !besideHub) {
                    PaneContent(innerEdge = PaneEdge.Start) {
                        Column {
                            KeywordsSection(listOf(TitleKeyword(1, "simulation")), onOpen = null)
                            TitleDetailsSection(listOf(DetailInfoEntry(Icons.Outlined.Info, "Status", "Released")))
                        }
                    }
                }
            }
        }
        assertEquals("Keywords heading and its first tag start together", tagStart("simulation"), start(keywords), TOLERANCE_DP)
        assertEquals("Details heading and its first fact start together", start("Status"), start(details), TOLERANCE_DP)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `the headings start where their content does on a phone`() = check()

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `the headings start where their content does on a 1280dp window`() = check()

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `the headings start where their content does with the hub beside the page`() = check(besideHub = true)

    private companion object {
        const val TOLERANCE_DP = 0.5f
    }
}
