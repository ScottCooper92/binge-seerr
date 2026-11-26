package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.component.BingeChoiceList
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
private val WORDS =
    FilterVocabulary(
        regions = REGIONS,
        defaultRegion = "GB",
        languages = listOf(FilterOption("en", "English"), FilterOption("ja", "Japanese")),
        providers = mapOf("GB" to PROVIDERS),
    )

/** The services tiles, and the choice list the language is picked from. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverWherePagesTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private fun left(text: String) =
        rule
            .onNodeWithText(text)
            .fetchSemanticsNode()
            .boundsInRoot.left

    private fun specs(vararg cases: Pair<FilterChoosing, DiscoverFilters>): List<FilterChoiceSpec> {
        var made = emptyList<FilterChoiceSpec>()
        rule.setContent { SeerrTheme { made = cases.map { (choosing, filters) -> filterChoiceSpec(choosing, filters, WORDS) } } }
        return made
    }

    @Test
    fun `the language list leads with Any and Any clears the language`() {
        val (language, unset) =
            specs(
                FilterChoosing.Language to DiscoverFilters(language = "ja"),
                FilterChoosing.Language to DiscoverFilters(),
            )

        assertEquals(listOf(ANY_LANGUAGE), language.pinned)
        assertEquals("ja", language.selected)
        assertEquals(ANY_LANGUAGE, unset.selected)
        assertEquals(listOf(ANY_LANGUAGE, "en", "ja"), (language.choices as BingeChoiceList.Ready).choices.map { it.value })
        assertEquals("en", DiscoverFilters().picked(FilterChoosing.Language, "en").language)
        assertNull(DiscoverFilters(language = "ja").picked(FilterChoosing.Language, ANY_LANGUAGE).language)
    }

    @Test
    fun `the services are listed in the order given`() {
        rule.setContent { SeerrTheme { WherePage(DiscoverFilters(), WORDS, Modifier, NO_LOADS, {}) } }

        assertEquals(PROVIDERS.map { it.name }, PROVIDERS.map { it.name }.sortedBy(::left))
    }

    @Test
    fun `ticking a service chooses it in the region shown`() {
        var changed: DiscoverFilters? = null
        rule.setContent { SeerrTheme { WherePage(DiscoverFilters(), WORDS, Modifier, NO_LOADS) { changed = it } } }

        rule.onNodeWithText("Disney Plus").performClick()

        assertEquals(DiscoverFilters(providers = setOf(337), region = "GB"), changed)
    }

    @Test
    fun `a region whose services have not arrived shows the loading tiles, not the empty words`() {
        rule.setContent { SeerrTheme { WherePage(DiscoverFilters(), WORDS.copy(providers = emptyMap()), Modifier, NO_LOADS, {}) } }

        rule.onNodeWithContentDescription("Loading streaming services").assertExists()
        rule.onAllNodesWithText("No streaming services listed for this region.").assertCountEquals(0)
    }

    @Test
    fun `a region whose services could not be read says so, not that it has none`() {
        rule.setContent {
            SeerrTheme {
                WherePage(
                    DiscoverFilters(),
                    WORDS.copy(providers = emptyMap(), providersFailed = setOf("GB")),
                    Modifier,
                    NO_LOADS,
                    {},
                )
            }
        }

        rule.onNodeWithText("Couldn’t load streaming services. Reopen this page to try again.").assertExists()
        rule.onAllNodesWithText("No streaming services listed for this region.").assertCountEquals(0)
    }

    @Test
    fun `a region with no services says so`() {
        rule.setContent {
            SeerrTheme { WherePage(DiscoverFilters(), WORDS.copy(providers = mapOf("GB" to emptyList())), Modifier, NO_LOADS, {}) }
        }

        rule.onNodeWithText("No streaming services listed for this region.").assertExists()
    }
}

private val NO_LOADS = FilterLoads({}, {}, {})
