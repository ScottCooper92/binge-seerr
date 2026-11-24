package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeChoiceSheetContent
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterCapabilities
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.preview.SeerrExpandedFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrExpandedSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTallComponentPreviews
import com.cooper.binge.companion.seerr.preview.SideSheetFrame
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 11, 18)

private const val LONG_LIST = 40

private val REGION_CODES =
    listOf(
        "AR",
        "AT",
        "AU",
        "BE",
        "BR",
        "CA",
        "CH",
        "CL",
        "CO",
        "CZ",
        "DE",
        "DK",
        "ES",
        "FI",
        "FR",
        "GB",
        "GR",
        "IE",
        "IN",
        "IT",
        "JP",
        "MX",
        "NL",
        "NO",
        "NZ",
        "PL",
        "PT",
        "SE",
        "US",
        "ZA",
    )

private val WORDS =
    FilterVocabulary(
        genres = listOf(FilterOption("18", "Drama"), FilterOption("35", "Comedy"), FilterOption("878", "Science Fiction")),
        languages = listOf(FilterOption("en", "English"), FilterOption("ja", "Japanese"), FilterOption("es", "Spanish")),
        capabilities = FilterCapabilities(watch = true, certification = true, keywords = true, excludeKeywords = true, status = true),
        regions =
            listOf(
                FilterOption("ES", "Spain"),
                FilterOption("GB", "United Kingdom"),
                FilterOption("US", "United States"),
                FilterOption("FR", "France"),
                FilterOption("DE", "Germany"),
            ),
        defaultRegion = "GB",
        providers =
            mapOf(
                "GB" to
                    listOf(
                        FilterOption("8", "Netflix"),
                        FilterOption("9", "Amazon Prime Video"),
                        FilterOption("337", "Disney Plus"),
                        FilterOption("39", "Now TV"),
                        FilterOption("29", "Sky Go"),
                        FilterOption("5", "ARROW"),
                    ),
            ),
        certifications =
            mapOf(
                "GB" to listOf("U", "PG", "12A", "15", "18"),
                "US" to listOf("G", "PG", "PG-13", "R"),
                "ES" to listOf("A", "7", "12", "16", "18"),
            ),
        keywordResults = listOf(FilterOption("9715", "superhero"), FilterOption("4344", "musical"), FilterOption("1721", "fight")),
        keywordNames = mapOf(9715 to "superhero", 4344 to "musical", 2343 to "a keyword with a rather long name to check the wrap"),
    )

private val SET =
    DiscoverFilters(
        genres = setOf(18, 878),
        language = "ja",
        releaseFrom = "2020-03-01",
        ratingMin = 7.0,
        runtimeMin = 90,
        runtimeMax = 150,
        voteCountMin = 1500,
        providers = setOf(8, 337),
        region = "GB",
        certificationCountry = "GB",
        certification = "15",
        keywords = setOf(9715, 4344),
        excludeKeywords = setOf(2343),
        status = setOf(0, 3),
    )

/** The filter sheet's rows as the 420dp end-edge side sheet of a foldable and a tablet, at 1x, 1.3x and 2x text and in Spanish. */
class DiscoverFilterSheetSideScreenshotTest {
    @PreviewTest
    @SeerrExpandedFontScalePreviews
    @Composable
    fun rowsFontScales() = SideRows(SET, TitleKind.Series)

    @PreviewTest
    @SeerrExpandedSpanishPreviews
    @Composable
    fun rowsSpanish() = SideRows(SET, TitleKind.Series)
}

@Composable
private fun SideRows(
    filters: DiscoverFilters,
    kind: TitleKind,
) {
    SideSheetFrame { Rows(filters, kind) }
}

/** The filter sheet's rows page, and the page a row opens, as the sheet shows them. */
class DiscoverFilterSheetScreenshotTest {
    /** The layout, once: the order chips, then a row for each filter with what it is set to. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun rows() = Rows(SET)

    /** Every row, the ones below the fold of a phone too: a show, with every filter set. */
    @PreviewTest
    @SeerrTallComponentPreviews
    @Composable
    fun rowsAll() = Rows(SET, TitleKind.Series)

    /** A show's rows: the same, with the Status row that only a series has. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun rowsSeries() = Rows(SET, TitleKind.Series)

    /** Nothing set: every row says Any and Clear all is not offered. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun rowsUnset() = Rows(DiscoverFilters())

    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun rowsFont13() = Rows(SET)

    /** At 2x the values stay on one line and are cut with an ellipsis, never over the arrow. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun rowsFontScale() = Rows(SET)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun rowsSpanish() = Rows(SET)

    /** The release date page: the windows as chips, and the custom From and To fields. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun release() = Section(DiscoverFilterSection.Release)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun releaseSpanish() = Section(DiscoverFilterSection.Release)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun releaseFontScale() = Section(DiscoverFilterSection.Release)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun genres() = Section(DiscoverFilterSection.Genres)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun genresSpanish() = Section(DiscoverFilterSection.Genres)

    /** The language row opens a choice sheet: Any first, then the server's languages. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun language() = Choice(FilterChoosing.Language, DiscoverFilters(language = "ja"), WORDS)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun runtime() = Section(DiscoverFilterSection.Runtime)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun rating() = Section(DiscoverFilterSection.Rating)

    /** A server's whole language list: sectioned, with a letter rail, as the choice sheet does it from forty entries. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun languageLong() =
        Choice(
            FilterChoosing.Language,
            DiscoverFilters(language = "l5"),
            FilterVocabulary(languages = (1..LONG_LIST).map { FilterOption("l$it", "Language $it") }),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun votes() = Section(DiscoverFilterSection.Votes)

    /** The region as a row, then the region's services in priority order, each a row with its logo and a search over them. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun where() = Section(DiscoverFilterSection.WhereToWatch)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun whereSpanish() = Section(DiscoverFilterSection.WhereToWatch)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun whereFontScale() = Section(DiscoverFilterSection.WhereToWatch)

    /** The choice sheet the region row opens: each country with its flag, the chosen one ticked. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun region() = Choice(FilterChoosing.Region, SET, WORDS)

    /** A long country list: Current, Suggested (the default region) and All, with a letter rail. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun regionLong() =
        Choice(
            FilterChoosing.Region,
            SET,
            WORDS.copy(regions = REGION_CODES.map { FilterOption(it, countryName(it)) }.sortedBy { it.name }),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun certification() = Section(DiscoverFilterSection.Certification)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun certificationSpanish() = Section(DiscoverFilterSection.Certification)

    /** The chosen keywords as chips, the field empty. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun keywords() = Section(DiscoverFilterSection.Keywords)

    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun excludedKeywordsFontScale() = Section(DiscoverFilterSection.ExcludeKeywords)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun status() = Section(DiscoverFilterSection.Status)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun statusSpanish() = Section(DiscoverFilterSection.Status)
}

@Composable
private fun Rows(
    filters: DiscoverFilters,
    kind: TitleKind = TitleKind.Movie,
) {
    Column(Modifier.fillMaxSize()) {
        DiscoverFilterSheetContent(
            draft = DiscoverFilterDraft(kind, DiscoverSort.PopularityDesc, filters),
            vocabulary = WORDS,
            today = TODAY,
            onKind = {},
            onSort = {},
            onOpen = {},
            onClearAll = {},
            onApply = {},
        )
    }
}

@Composable
private fun Choice(
    choosing: FilterChoosing,
    filters: DiscoverFilters,
    words: FilterVocabulary,
) {
    val spec = filterChoiceSpec(choosing, filters, words)
    BingeChoiceSheetContent(
        title = spec.title,
        choices = spec.choices,
        selected = spec.selected,
        onSelect = {},
        suggested = spec.suggested,
        pinned = spec.pinned,
        filterPlaceholder = spec.filterPlaceholder,
    )
}

@Composable
private fun Section(section: DiscoverFilterSection) {
    Column {
        DiscoverSectionPage(section, SET, WORDS, TODAY, FilterLoads({}, {}, {}), onOpenRegion = {}, onChange = {}, onDone = {})
    }
}
