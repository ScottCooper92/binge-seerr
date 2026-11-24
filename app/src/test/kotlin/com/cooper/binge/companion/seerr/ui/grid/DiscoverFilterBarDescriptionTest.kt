package com.cooper.binge.companion.seerr.ui.grid

import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import com.cooper.binge.companion.seerr.feature.discover.R as DiscoverR

/** The Filters pill is one accessibility item, so its description is where a screen reader learns how many filters apply. */
@RunWith(RobolectricTestRunner::class)
class DiscoverFilterBarDescriptionTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun descriptionWith(groups: List<FilterGroup>): String? {
        var description: String? = null
        rule.setContent {
            SeerrTheme {
                description =
                    GridControls(sort = DiscoverSort.RatingDesc, chip = null, groups = groups)
                        .toFilterBar(LocalDate.of(2026, 1, 1))
                        ?.pillDescription
            }
        }
        return description
    }

    @Test
    fun `with no filter applied the pill says only that it opens them`() {
        val expected = ApplicationProvider.getApplicationContext<android.content.Context>().getString(DiscoverR.string.grid_open_filters)
        assertEquals(expected, descriptionWith(emptyList()))
    }

    @Test
    fun `with filters applied the pill says how many`() {
        val expected =
            ApplicationProvider.getApplicationContext<android.content.Context>().resources.getQuantityString(
                DiscoverR.plurals.grid_open_filters_applied,
                2,
                2,
            )
        assertEquals(expected, descriptionWith(listOf(FilterGroup.Genres, FilterGroup.Rating)))
    }
}
