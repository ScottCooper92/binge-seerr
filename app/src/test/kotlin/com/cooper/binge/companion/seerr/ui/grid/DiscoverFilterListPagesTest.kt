package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.binge.designsystem.template.FilterSectionContent
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
                        loads = FilterLoads({}, {}, {}),
                        onOpenRegion = {},
                        onChange = { filters.value = it },
                        onDone = {},
                    )
                }
            }
        }
    }

    private fun showGenres() {
        rule.setContent {
            BingeExpressiveTheme {
                Column {
                    val spec = DiscoverFilterSection.Genres.spec(filters.value, words, LocalDate.of(2026, 11, 18)) { filters.value = it }!!
                    FilterSectionContent(
                        section = spec.ui,
                        clearLabel = "Clear",
                        clearEnabled = spec.active,
                        doneLabel = "Close",
                        onClear = {},
                        onDone = {},
                        handlers = spec.handlers,
                        footerAccessory = spec.accessory,
                    )
                }
            }
        }
    }

    @Test
    fun `a genre chip is picked and unpicked`() {
        showGenres()
        rule.onNodeWithText("Comedy").performClick()
        assertEquals(setOf(35), filters.value.genres)
        rule.onNodeWithText("Comedy").performClick()
        assertEquals(emptySet<Int>(), filters.value.genres)
    }

    @Test
    fun `the join toggle is inert until two genres are picked, then switches between any and all`() {
        filters.value = DiscoverFilters(genres = setOf(28))
        showGenres()
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_join_or)).performClick()
        assertEquals(false, filters.value.genresAny)
        filters.value = filters.value.copy(genres = setOf(28, 35))
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_join_or)).performClick()
        assertTrue(filters.value.genresAny)
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_join_or)).assertIsSelected()
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_join_and)).performClick()
        assertEquals(false, filters.value.genresAny)
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_join_and)).assertIsSelected()
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_join_or)).assertIsNotSelected()
    }

    private fun showCertification() {
        rule.setContent {
            BingeExpressiveTheme {
                Column {
                    val spec =
                        DiscoverFilterSection.Certification.spec(filters.value, words, LocalDate.of(2026, 11, 18)) { filters.value = it }!!
                    FilterSectionContent(
                        section = spec.ui,
                        clearLabel = "Clear",
                        clearEnabled = spec.active,
                        doneLabel = "Close",
                        onClear = {},
                        onDone = {},
                        hint = spec.hint,
                        handlers = spec.handlers,
                    )
                }
            }
        }
    }

    @Test
    fun `a tap on a rating sets the highest allowed and the ratings of the server's region are offered`() {
        showCertification()
        rule.onNodeWithText("R").performClick()
        assertEquals("R", filters.value.certificationLte)
        assertEquals(null, filters.value.certificationGte)
        assertEquals("US", filters.value.certificationCountry)
        rule.onNodeWithText("15").assertDoesNotExist()
    }

    @Test
    fun `a long press on a rating sets the lowest allowed`() {
        showCertification()
        rule.onNodeWithText("PG").performTouchInput { longClick() }
        assertEquals("PG", filters.value.certificationGte)
        assertEquals(null, filters.value.certificationLte)
    }

    @Test
    fun `pressing a rating until it clears leaves no country behind`() {
        showCertification()
        repeat(3) { rule.onNodeWithText("R").performClick() }
        assertEquals(DiscoverFilters(), filters.value)
    }

    @Test
    fun `a show status is ticked from its row`() {
        show(DiscoverFilterSection.Status)
        rule.onNodeWithText(string(DiscoverR.string.grid_filter_status_ended)).performClick()
        assertEquals(setOf(3), filters.value.status)
    }
}
