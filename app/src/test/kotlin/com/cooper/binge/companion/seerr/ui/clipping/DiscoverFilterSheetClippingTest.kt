package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterCapabilities
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.ui.grid.DiscoverFilterDraft
import com.cooper.binge.companion.seerr.ui.grid.DiscoverFilterSheetContent
import com.cooper.binge.companion.seerr.ui.grid.FilterVocabulary
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private val WORDS =
    FilterVocabulary(
        genres = listOf(FilterOption("18", "Drama"), FilterOption("878", "Science Fiction and Fantasy")),
        languages = listOf(FilterOption("ja", "Japanese")),
        capabilities = FilterCapabilities(watch = true, certification = true, keywords = true, excludeKeywords = true, status = true),
        regions = listOf(FilterOption("GB", "United Kingdom")),
        defaultRegion = "GB",
        providers =
            mapOf(
                "GB" to listOf(FilterOption("8", "Netflix"), FilterOption("337", "Disney Plus"), FilterOption("9", "Amazon Prime Video")),
            ),
        certifications = mapOf("GB" to listOf("U", "15")),
        keywordNames = mapOf(1 to "a keyword with a rather long name", 2 to "another keyword"),
    )

private val FILTERS =
    DiscoverFilters(
        genres = setOf(18, 878),
        language = "ja",
        providers = setOf(8, 337, 9),
        region = "GB",
        certificationCountry = "GB",
        certification = "15",
        keywords = setOf(1, 2),
        excludeKeywords = setOf(1, 2),
        status = setOf(0, 1, 2, 3, 4, 5),
    )

/** The filter sheet's rows with every filter set to its longest, at font scales 1.3 and 2.0, as a series has them all. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h4000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverFilterSheetClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) =
        rule.assertNoClippingAt(fontScale) {
            DiscoverFilterSheetContent(
                draft = DiscoverFilterDraft(TitleKind.Series, DiscoverSort.PopularityDesc, FILTERS),
                vocabulary = WORDS,
                today = LocalDate.of(2026, 11, 18),
                onKind = {},
                onSort = {},
                onOpen = {},
                onClearAll = {},
            )
        }

    @Test fun `every row at 1_3`() = check(FONT_SCALE_13)

    @Test fun `every row at 2_0`() = check(FONT_SCALE_20)
}

/** The same rows in Spanish, whose labels are the longer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h4000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverFilterSheetSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `every row at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20) {
            DiscoverFilterSheetContent(
                draft = DiscoverFilterDraft(TitleKind.Series, DiscoverSort.PopularityDesc, FILTERS),
                vocabulary = WORDS,
                today = LocalDate.of(2026, 11, 18),
                onKind = {},
                onSort = {},
                onOpen = {},
                onClearAll = {},
            )
        }
}
