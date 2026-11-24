package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import com.cooper.binge.companion.seerr.feature.discover.R as DiscoverR

/** The filter pages with long vocabularies are lists to tick, with only short choices left as one row of chips. */
@RunWith(RobolectricTestRunner::class)
class DiscoverFilterListPagesTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val filters = mutableStateOf(DiscoverFilters())
    private val words =
        FilterVocabulary(
            genres = listOf(FilterOption("28", "Action"), FilterOption("35", "Comedy")),
            certifications = mapOf("US" to listOf("PG", "R"), "GB" to listOf("12A", "15")),
            defaultRegion = "US",
        )

    private fun show(section: DiscoverFilterSection) {
        rule.setContent {
            BingeExpressiveTheme {
                Column {
                    DiscoverSectionPage(
                        section = section,
                        filters = filters.value,
                        vocabulary = words,
                        today = LocalDate.of(2026, 11, 18),
                        loads = FilterLoads({}, {}, {}),
                        onOpenRegion = {},
                        onChange = { filters.value = it },
                        onDone = {},
                    )
                }
            }
        }
    }

    @Test
    fun `a genre is ticked and unticked from its row`() {
        show(DiscoverFilterSection.Genres)
        rule.onNodeWithText("Comedy").performClick()
        assertEquals(setOf(35), filters.value.genres)
        rule.onNodeWithText("Comedy").performClick()
        assertEquals(emptySet<Int>(), filters.value.genres)
    }

    @Test
    fun `match any is a chip beside match all`() {
        show(DiscoverFilterSection.Genres)
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_match_any)).performClick()
        assertTrue(filters.value.genresAny)
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_match_all)).performClick()
        assertEquals(false, filters.value.genresAny)
    }

    @Test
    fun `a rating is a chip that sets and clears the choice`() {
        show(DiscoverFilterSection.Certification)
        rule.onNodeWithText("R").performClick()
        assertEquals("R", filters.value.certification)
        rule.onNodeWithText("R").performClick()
        assertEquals(null, filters.value.certification)
    }

    @Test
    fun `a country is picked from its row and clears the rating`() {
        filters.value = DiscoverFilters(certificationCountry = "US", certification = "R")
        show(DiscoverFilterSection.Certification)
        rule.onNodeWithText(countryName("GB")).performClick()
        assertEquals("GB", filters.value.certificationCountry)
        assertEquals(null, filters.value.certification)
        rule.onNodeWithText(countryName("GB")).assertIsSelected()
    }

    @Test
    fun `a show status is ticked from its row`() {
        show(DiscoverFilterSection.Status)
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_status_ended)).performClick()
        assertEquals(setOf(3), filters.value.status)
    }
}
