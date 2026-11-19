package com.cooper.binge.companion.seerr.ui.search.title

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

private fun ready(
    loaded: Int,
    hasMore: Boolean,
) = TitleSearchUiState.Ready(
    text = "matrix",
    results = emptyList(),
    loaded = loaded,
    type = SearchType.People,
    counts = mapOf(SearchType.All to 50, SearchType.Movies to loaded, SearchType.Series to 0, SearchType.People to 0),
    genreNames = emptyMap(),
    hasMore = hasMore,
    appending = false,
    appendFailed = false,
)

/** A type chosen that the pages so far hold none of: the screen keeps asking for pages, and says so when they run out. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleSearchPagingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a page that adds none of the chosen type is followed by another request`() {
        var loads = 0
        var state by mutableStateOf<TitleSearchUiState>(ready(loaded = 20, hasMore = true))
        rule.setContent {
            SeerrTheme { TitleSearchScreen(state, TitleSearchActions({}, {}, {}, {}, { loads++ }, {}, {}, {}, {}, { _, _ -> })) }
        }
        rule.waitForIdle()
        assertEquals(1, loads)

        state = ready(loaded = 40, hasMore = true)
        rule.waitForIdle()
        assertEquals("the next page was asked for although the filtered list is still empty", 2, loads)
    }

    @Test
    fun `an empty group with no more pages says so`() {
        rule.setContent {
            SeerrTheme {
                TitleSearchScreen(
                    ready(loaded = 40, hasMore = false),
                    TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }),
                )
            }
        }
        val title = ApplicationProvider.getApplicationContext<Context>().getString(R.string.title_search_type_none_title)
        rule.onNodeWithText(title).assertIsDisplayed()
    }
}
