package com.cooper.binge.companion.seerr.ui.grid

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private const val GENRES = 180

private val WORDS = FilterVocabulary(genres = (1..GENRES).map { FilterOption("$it", "Genre $it") })

/** A page taller than the sheet scrolls, and its Done stays where it is: a long genre list stands in for any page taller than the sheet. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverFilterScrollTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the last genre can be reached and Done is still on screen`() {
        rule.setContent {
            Column(Modifier.height(500.dp)) {
                DiscoverSectionPage(
                    section = DiscoverFilterSection.Genres,
                    filters = DiscoverFilters(),
                    vocabulary = WORDS,
                    today = LocalDate.of(2026, 11, 18),
                    loads = FilterLoads({}, {}, {}),
                    onOpenRegion = {},
                    onChange = {},
                    onDone = {},
                )
            }
        }
        rule.onNodeWithText("Genre $GENRES").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(R.string.grid_filters_done)).assertIsDisplayed()
    }
}
