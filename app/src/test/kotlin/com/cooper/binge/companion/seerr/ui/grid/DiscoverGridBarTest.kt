package com.cooper.binge.companion.seerr.ui.grid

import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.template.GridFilterBar
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterGroup
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * What the grid hands the design system's template for its bar (#2336): the subtitle that carries the preset and the order under the
 * title "Discover", and the Filters pill with the filters in force, which the template also draws as the collapsed bar's action.
 */
@RunWith(RobolectricTestRunner::class)
class DiscoverGridBarTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun subtitle(
        controls: GridControls,
        kind: TitleKind = TitleKind.Movie,
        name: String? = null,
    ): String {
        var text = ""
        rule.setContent { SeerrTheme { text = controls.subtitle(kind, name) } }
        return text
    }

    private fun bar(controls: GridControls): GridFilterBar? {
        var bar: GridFilterBar? = null
        rule.setContent { SeerrTheme { bar = controls.toFilterBar(LocalDate.of(2026, 1, 1)) } }
        return bar
    }

    @Test
    fun `the subtitle names the preset and then the order`() {
        val controls = GridControls(sort = DiscoverSort.RatingDesc, chip = null)

        assertEquals("Popular movies · ${string(R.string.grid_sort_rating_desc)}", subtitle(controls, name = "Popular movies"))
    }

    @Test
    fun `a movie list with no name is called Movies`() {
        val controls = GridControls(sort = DiscoverSort.PopularityDesc, chip = null)

        assertEquals("${string(R.string.hubs_title_movies)} · ${string(R.string.grid_sort_popularity_desc)}", subtitle(controls))
    }

    @Test
    fun `a series list with no name is called TV`() {
        val controls = GridControls(sort = DiscoverSort.PopularityDesc, chip = null)

        assertEquals(
            "${string(R.string.hubs_title_tv)} · ${string(R.string.grid_sort_popularity_desc)}",
            subtitle(controls, TitleKind.Series),
        )
    }

    @Test
    fun `a list that takes no order says only its name`() {
        assertEquals("Upcoming", subtitle(GridControls(sort = null, chip = null), name = "Upcoming"))
    }

    @Test
    fun `a list that takes no order has no Filters pill`() {
        assertNull(bar(GridControls(sort = null, chip = null)))
    }

    @Test
    fun `the Filters pill is worded and counts the filters the user added`() {
        val bar =
            bar(GridControls(sort = DiscoverSort.PopularityDesc, chip = null, groups = listOf(FilterGroup.Genres, FilterGroup.Rating)))

        assertEquals(string(R.string.grid_filters_button), bar?.pillLabel)
        assertEquals(2, bar?.activeCount)
    }

    @Test
    fun `the preset's own chip comes first and each added filter after it, keyed to clear the right one`() {
        val controls =
            GridControls(
                sort = DiscoverSort.PopularityDesc,
                chip = ActiveFilterChip(ChipKind.Genre, label = "Horror"),
                groups = listOf(FilterGroup.Rating, FilterGroup.Genres),
            )

        val chips = bar(controls)?.chips.orEmpty()

        assertEquals(listOf("filter", FilterGroup.Rating.name, FilterGroup.Genres.name), chips.map { it.key })
        assertEquals("Horror", chips.first().label)
    }

    @Test
    fun `a preset with no name is worded by its kind`() {
        val controls = GridControls(sort = DiscoverSort.PopularityDesc, chip = ActiveFilterChip(ChipKind.Upcoming, label = null))

        assertEquals(string(R.string.grid_chip_upcoming), bar(controls)?.chips?.single()?.label)
    }
}
