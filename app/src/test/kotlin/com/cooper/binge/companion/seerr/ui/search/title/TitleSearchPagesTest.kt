package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.template.SearchState
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private val MIXED =
    listOf(
        TitleResult(TitleKind.Movie, 1, "Alpha Film", null, null, null, null, null, TitleAvailability.None, null, emptyList()),
        TitleResult(TitleKind.Series, 2, "Beta Show", null, null, null, null, null, TitleAvailability.None, null, emptyList()),
        TitleResult(TitleKind.Person, 3, "Gamma Person", null, null, null, null, null, TitleAvailability.None, null, emptyList()),
    )

/** Each page of the type pager lists its own type from every title the search found, whichever chip is chosen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleSearchPagesTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private fun pagesWith(vararg types: SearchType): List<Map<String, List<String>>> {
        val states = mutableListOf<SearchState>()
        rule.setContent {
            SeerrTheme {
                types.forEach { type ->
                    val ready =
                        TitleSearchUiState.Ready(
                            text = "x",
                            results = MIXED.filter(type::keeps),
                            loaded = MIXED.size,
                            type = type,
                            counts = MIXED.typeCounts(),
                            genreNames = emptyMap(),
                            hasMore = false,
                            appending = false,
                            appendFailed = false,
                            hasStudios = false,
                            pool = MIXED,
                        )
                    states += ready.toSearchState()
                }
            }
        }
        rule.waitForIdle()
        return states.takeLast(types.size).map { state ->
            (state as SearchState.Ready).pages.orEmpty().mapValues { (_, rows) -> rows.map { it.name } }
        }
    }

    @Test
    fun `choosing Movies leaves the other pages with their own rows`() {
        val pages = pagesWith(SearchType.Movies).single()

        assertEquals(listOf("Alpha Film"), pages.getValue(SearchType.Movies.name))
        assertEquals(listOf("Beta Show"), pages.getValue(SearchType.Series.name))
        assertEquals(listOf("Gamma Person"), pages.getValue(SearchType.People.name))
        assertEquals(listOf("Alpha Film", "Beta Show", "Gamma Person"), pages.getValue(SearchType.All.name))
    }

    @Test
    fun `the pages are the same whichever chip is chosen`() {
        val (all, people) = pagesWith(SearchType.All, SearchType.People)

        assertEquals(all, people)
    }
}
