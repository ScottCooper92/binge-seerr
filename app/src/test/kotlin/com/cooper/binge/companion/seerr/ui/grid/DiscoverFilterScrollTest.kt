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

private const val LANGUAGES = 180

private val WORDS = FilterVocabulary(languages = (1..LANGUAGES).map { FilterOption("l$it", "Language $it") })

/** A page taller than the sheet scrolls, and its Done stays where it is: a server's whole language list is some 180 rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverFilterScrollTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the last language can be reached and Done is still on screen`() {
        rule.setContent {
            Column(Modifier.height(500.dp)) {
                DiscoverSectionPage(
                    section = DiscoverFilterSection.Language,
                    filters = DiscoverFilters(),
                    vocabulary = WORDS,
                    today = LocalDate.of(2026, 11, 18),
                    onChange = {},
                    onDone = {},
                )
            }
        }
        rule.onNodeWithText("Language $LANGUAGES").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(R.string.grid_filters_done)).assertIsDisplayed()
    }
}
