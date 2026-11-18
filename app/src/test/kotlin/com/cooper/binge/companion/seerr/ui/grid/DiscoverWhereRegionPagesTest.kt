package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private val REGIONS = listOf(FilterOption("ES", "Spain"), FilterOption("GB", "United Kingdom"), FilterOption("US", "United States"))
private val PROVIDERS =
    listOf(
        FilterOption("8", "Netflix"),
        FilterOption("337", "Disney Plus"),
        FilterOption("9", "Amazon Prime Video"),
        FilterOption("5", "ARROW"),
    )
private val WORDS = FilterVocabulary(regions = REGIONS, defaultRegion = "GB", providers = mapOf("GB" to PROVIDERS))

/** The region page and the services page: the chosen country first and ticked, the services in the order given, each with a search. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverWhereRegionPagesTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private fun top(text: String) =
        rule
            .onNodeWithText(text)
            .fetchSemanticsNode()
            .boundsInRoot.top

    @Test
    fun `the chosen region is listed first and ticked`() {
        rule.setContent { SeerrTheme { RegionPage(DiscoverFilters(), WORDS) {} } }

        assertEquals(listOf("United Kingdom", "Spain", "United States"), listOf("Spain", "United Kingdom", "United States").sortedBy(::top))
        rule.onNode(hasText("United Kingdom") and isSelectable()).assertIsSelected()
    }

    @Test
    fun `a region the user set wins over the default`() {
        rule.setContent { SeerrTheme { RegionPage(DiscoverFilters(region = "US"), WORDS) {} } }

        assertEquals("United States", listOf("Spain", "United Kingdom", "United States").minBy(::top))
        rule.onNode(hasText("United States") and isSelectable()).assertIsSelected()
    }

    @Test
    fun `typing in the search narrows the countries`() {
        rule.setContent { SeerrTheme { RegionPage(DiscoverFilters(), WORDS) {} } }

        rule.onNode(hasSetTextAction()).performTextInput("spa")

        rule.onNodeWithText("Spain").assertExists()
        rule.onAllNodesWithText("United", substring = true).assertCountEquals(0)
    }

    @Test
    fun `choosing another country sets it and clears the services`() {
        var changed: DiscoverFilters? = null
        rule.setContent { SeerrTheme { RegionPage(DiscoverFilters(providers = setOf(8)), WORDS) { changed = it } } }

        rule.onNodeWithText("Spain").performClick()

        assertEquals(DiscoverFilters(region = "ES", providers = emptySet()), changed)
    }

    @Test
    fun `the services are listed in the order given and a search narrows them`() {
        rule.setContent { SeerrTheme { WherePage(DiscoverFilters(), WORDS, Modifier, NO_LOADS, {}, {}) } }

        assertEquals(PROVIDERS.map { it.name }, PROVIDERS.map { it.name }.sortedBy(::top))

        rule.onNode(hasSetTextAction()).performTextInput("prime")

        rule.onNodeWithText("Amazon Prime Video").assertExists()
        rule.onAllNodesWithText("Netflix").assertCountEquals(0)
    }

    @Test
    fun `ticking a service chooses it in the region shown`() {
        var changed: DiscoverFilters? = null
        rule.setContent { SeerrTheme { WherePage(DiscoverFilters(), WORDS, Modifier, NO_LOADS, {}) { changed = it } } }

        rule.onNodeWithText("Disney Plus").performClick()

        assertEquals(DiscoverFilters(providers = setOf(337), region = "GB"), changed)
    }
}

private val NO_LOADS = FilterLoads({}, {}, {})
