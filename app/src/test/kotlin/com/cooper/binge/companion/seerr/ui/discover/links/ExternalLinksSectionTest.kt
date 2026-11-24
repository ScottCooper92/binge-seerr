package com.cooper.binge.companion.seerr.ui.discover.links

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.discover.ExternalLink
import com.cooper.binge.companion.seerr.discover.ExternalLinkKind
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.discover.R as DiscoverR

@RunWith(RobolectricTestRunner::class)
class ExternalLinksSectionTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val links =
        listOf(
            ExternalLink(ExternalLinkKind.Imdb, "https://www.imdb.com/title/tt1"),
            ExternalLink(ExternalLinkKind.Tmdb, "https://www.themoviedb.org/movie/1"),
            ExternalLink(ExternalLinkKind.Website, "https://film.example.org/"),
        )

    private fun open(
        links: List<ExternalLink>,
        onOpen: (String) -> Unit = {},
    ) = rule.setContent { SeerrTheme { ExternalLinksSection(links, onOpen) } }

    @Test
    fun `each link is a chip that opens its address`() {
        val opened = mutableListOf<String>()
        open(links) { opened += it }

        rule.onNodeWithText(resources.getString(DiscoverR.string.external_link_imdb), useUnmergedTree = true).performClick()
        rule.onNodeWithText(resources.getString(DiscoverR.string.external_link_tmdb), useUnmergedTree = true).performClick()
        rule.onNodeWithText(resources.getString(DiscoverR.string.external_link_website), useUnmergedTree = true).performClick()

        assertEquals(links.map { it.url }, opened)
    }

    @Test
    fun `a title with no links draws no heading`() {
        open(emptyList())

        rule.onNodeWithText(resources.getString(DiscoverR.string.external_links_heading)).assertDoesNotExist()
    }
}
