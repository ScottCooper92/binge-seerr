package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.Context
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.TitleLink
import com.cooper.binge.companion.seerr.discover.title.TitleLinkKind
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TitleLinksSectionTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val links =
        listOf(
            TitleLink(TitleLinkKind.Imdb, "https://www.imdb.com/title/tt1"),
            TitleLink(TitleLinkKind.Tmdb, "https://www.themoviedb.org/movie/1"),
            TitleLink(TitleLinkKind.Website, "https://film.example.org/"),
        )

    private fun open(
        links: List<TitleLink>,
        onOpen: (String) -> Unit = {},
    ) = rule.setContent { SeerrTheme { TitleLinksSection(links, onOpen) } }

    @Test
    fun `each link is a chip that opens its address`() {
        val opened = mutableListOf<String>()
        open(links) { opened += it }

        rule.onNodeWithText(resources.getString(R.string.person_page_imdb), useUnmergedTree = true).performClick()
        rule.onNodeWithText(resources.getString(R.string.title_link_tmdb), useUnmergedTree = true).performClick()
        rule.onNodeWithText(resources.getString(R.string.person_page_website), useUnmergedTree = true).performClick()

        assertEquals(links.map { it.url }, opened)
    }

    @Test
    fun `a title with no links draws no heading`() {
        open(emptyList())

        rule.onNodeWithText(resources.getString(R.string.person_page_links)).assertDoesNotExist()
    }
}
