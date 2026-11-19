package com.cooper.binge.companion.seerr.ui.discover.person

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A person's links leave the app, so each chip says so to a screen reader and opens its own page. */
@RunWith(RobolectricTestRunner::class)
class PersonLinksRowTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val links =
        listOf(
            PersonLink(PersonLinkKind.Imdb, "https://imdb.example/p"),
            PersonLink(PersonLinkKind.Website, "https://site.example"),
        )

    private fun show(opened: MutableList<String> = mutableListOf()) =
        rule.setContent {
            SeerrTheme {
                PersonLinksRow(links, onOpen = {
                    opened +=
                        it
                })
            }
        }

    @Test
    fun `each chip says it opens outside the app`() {
        show()

        rule.onNodeWithContentDescription("IMDb, opens outside the app").assertHasClickAction()
        rule.onNodeWithContentDescription("Website, opens outside the app").assertHasClickAction()
    }

    @Test
    fun `a tap opens that chip's page`() {
        val opened = mutableListOf<String>()
        show(opened)

        rule.onNodeWithContentDescription("Website, opens outside the app").performClick()

        assertEquals(listOf("https://site.example"), opened)
    }

    @Test
    fun `no links draws no chips`() {
        rule.setContent { SeerrTheme { PersonLinksRow(emptyList(), onOpen = {}) } }

        rule.onAllNodesWithContentDescription("IMDb, opens outside the app").assertCountEquals(0)
    }
}
