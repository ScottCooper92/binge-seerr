package com.cooper.binge.companion.seerr.ui.discover.links

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.TagRow
import com.binge.designsystem.template.readingInset
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.ExternalLink
import com.cooper.binge.companion.seerr.discover.ExternalLinkKind
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The heading of External links sits as far above its chips as the Keywords section's does, whatever the font size, because both are
 * one column of a heading and a chip row, with nothing of the page's own spacing between them.
 */
@RunWith(RobolectricTestRunner::class)
class ExternalLinksSpacingTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun gapsAt(fontScale: Float): Pair<Dp, Dp> {
        val density = Density(density = 2f, fontScale = fontScale)
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalDensity provides density) {
                    // The page's own column spaces what it holds, as the template's does.
                    Column(verticalArrangement = Arrangement.spacedBy(PAGE_GAP)) {
                        Column {
                            SectionHeader("Keywords", horizontalPadding = readingInset())
                            TagRow(tags = listOf("alpha", "beta"), modifier = Modifier.padding(horizontal = readingInset()), compact = true)
                        }
                        ExternalLinksSection(listOf(ExternalLink(ExternalLinkKind.Imdb, "https://www.imdb.com/title/tt1")), {})
                    }
                }
            }
        }
        val keywordsGap =
            rule
                .onNodeWithText("alpha", useUnmergedTree = true)
                .onParent()
                .getUnclippedBoundsInRoot()
                .top -
                rule.onNodeWithText("Keywords", useUnmergedTree = true).getUnclippedBoundsInRoot().bottom
        val linksGap =
            rule
                .onNode(
                    hasClickAction() and hasAnyDescendant(hasText(resources.getString(R.string.external_link_imdb))),
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot()
                .top -
                rule
                    .onNodeWithText(
                        resources.getString(R.string.external_links_heading),
                        useUnmergedTree = true,
                    ).getUnclippedBoundsInRoot()
                    .bottom
        return keywordsGap to linksGap
    }

    @Test
    fun `the links heading is as far above its chips as the keywords heading at font scale 1`() {
        val (keywords, links) = gapsAt(1f)

        assertEquals(keywords.value, links.value, TOLERANCE)
    }

    @Test
    fun `the links heading is as far above its chips as the keywords heading at font scale 2`() {
        val (keywords, links) = gapsAt(2f)

        assertEquals(keywords.value, links.value, TOLERANCE)
    }

    private companion object {
        val PAGE_GAP = 24.dp

        /** A link chip and a keyword tag are different components, and their outlines differ by a dp; the page's own spacing was 14dp or more. */
        const val TOLERANCE = 1.01f
    }
}
