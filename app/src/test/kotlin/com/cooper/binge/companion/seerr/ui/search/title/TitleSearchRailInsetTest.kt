package com.cooper.binge.companion.seerr.ui.search.title

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.LocalNavOverlayInsets
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
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

private val RAIL = 80.dp
private val GUTTER = 16.dp
private val MOVIE = TitleResult(TitleKind.Movie, 1, "One", "2020", null, "/b.jpg", null, null, TitleAvailability.None, null)

/** On a wide window the navigation rail overlays the start edge; the idle Search page clears it once, not twice. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w600dp-h800dp-xhdpi")
class TitleSearchRailInsetTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun text(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun showIdle(
        recents: List<String>,
        trending: Trending,
    ) {
        val actions = TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> })
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalNavOverlayInsets provides PaddingValues(start = RAIL)) {
                    TitleSearchScreen(TitleSearchUiState.Idle(text = "", recents = recents, trending = trending), actions)
                }
            }
        }
    }

    @Test
    fun `the recents and the trending rails start at the same place, one rail inset in`() {
        showIdle(listOf("one", "two"), Trending.Ready(movies = listOf(MOVIE), series = emptyList()))

        val recents = rule.onNodeWithText(text(R.string.title_search_recent)).getBoundsInRoot().left
        val rail = rule.onNodeWithText(text(R.string.title_search_trending_movies)).getBoundsInRoot().left

        assertEquals("the two headings line up", rail.value, recents.value, 0.5f)
        assertEquals("one rail and one gutter in", (RAIL + GUTTER).value, recents.value, 0.5f)
    }

    @Test
    fun `the prompt with nothing to offer is centred in what the rail leaves`() {
        showIdle(emptyList(), Trending.Unavailable)

        val prompt = rule.onNodeWithText(text(R.string.title_search_idle_title)).getBoundsInRoot()

        assertTrue("clear of the rail, ${prompt.left}", prompt.left >= RAIL)
    }
}
