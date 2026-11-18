package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.preview.SeerrExpandedFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrExpandedSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrNarrowTabletPreview
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview
import kotlinx.coroutines.flow.emptyFlow

private const val SCROLLED_PX = 2500
private const val FACTS_PX = 1400
private const val WATCH_PX = 2900
private const val WATCH_LARGE_FONT_PX = 5200
private const val COLLECTION_PX = 3800
private const val COLLECTION_LARGE_FONT_PX = 6900
private const val FACTS_LARGE_FONT_PX = 2700

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

/** As [NO_ACTIONS], but a studio opens its grid, so the studios are links in the accent colour. */
private val LINKED_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onOpenCompany = {})

/** As [NO_ACTIONS], but no page opens for a collection, as on the phone today: the rail has no See all. */
private val NO_COLLECTION_PAGE = TitlePageActions({}, {}, {}, {}, {}, {}, {}, null, {}, {})

@Composable
private fun Frame(state: TitlePageUiState) = TitlePageScreen(state, emptyFlow(), NO_ACTIONS)

/** The film page: the layout once across the device matrix, then each state of it on the phone. */
class TitlePageScreenshotTest {
    /** A requestable film with everything the server gives. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun requestable() = Frame(TitlePageUiState.Ready(fullMovie(), fullExtras()))

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun requestableTablet() = Frame(TitlePageUiState.Ready(fullMovie(), fullExtras()))

    /** A window wider than the reading column but short of the expanded line: the sections share one left edge. */
    @PreviewTest
    @SeerrNarrowTabletPreview
    @Composable
    fun requestableNarrowTablet() = Frame(TitlePageUiState.Ready(fullMovie(), fullExtras()))

    @PreviewTest
    @SeerrExpandedSpanishPreviews
    @Composable
    fun requestableExpandedSpanish() = Frame(TitlePageUiState.Ready(fullMovie(), fullExtras()))

    @PreviewTest
    @SeerrExpandedFontScalePreviews
    @Composable
    fun requestableExpandedLargeText() = Frame(TitlePageUiState.Ready(fullMovie(), fullExtras()))

    /** Already in the library: no Request button, and the status chip says so. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun available() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Available, mayRequest = false), fourK = null),
                fullExtras(),
            ),
        )

    /** Standard held and 4K still open: the button stays, and both versions are named. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun fourKOpen() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(
                    standard = VersionState(TitleAvailability.Available, mayRequest = false),
                    fourK = VersionState(TitleAvailability.None, mayRequest = true),
                ),
                fullExtras(),
            ),
        )

    /** Blocklisted: nothing to request. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun blocklisted() =
        Frame(TitlePageUiState.Ready(fullMovie(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false), fourK = null)))

    /** The sections that load apart have not arrived yet. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun extrasPending() = Frame(TitlePageUiState.Ready(fullMovie()))

    /** The server knows almost nothing: every empty section is left out. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun sparse() = Frame(TitlePageUiState.Ready(sparseMovie()))

    /** Scrolled well past the hero: the top bar has its surface, so the back arrow and title do not draw over the content. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scrolled() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = SCROLLED_PX),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(TitlePageUiState.Loading)

    /** The skeleton at font scale 2, where the stats go two to a row. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun loadingLargeFonts() = Frame(TitlePageUiState.Loading)

    /** The longest title, tagline and names at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(TitlePageUiState.Ready(longMovie(), fullExtras()))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(TitlePageUiState.Ready(longMovie(), fullExtras()))

    /** The facts list at font scale 2, with the longest studio names: a label and its value stay readable. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun factsLargeFonts() =
        TitlePageScreen(
            TitlePageUiState.Ready(longMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = FACTS_LARGE_FONT_PX),
        )

    /** The facts with the studios as links: each name in the accent colour, where a tap opens that studio's grid. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun factsLinked() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie().copy(companiesOpen = true), fullExtras()),
            emptyFlow(),
            LINKED_ACTIONS,
            scrollState = rememberScrollState(initial = FACTS_PX),
        )

    /** Linked studios with the longest names at font scale 2: the links wrap and none is cut off. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun factsLinkedLargeFonts() =
        TitlePageScreen(
            TitlePageUiState.Ready(longMovie().copy(companiesOpen = true), fullExtras()),
            emptyFlow(),
            LINKED_ACTIONS,
            scrollState = rememberScrollState(initial = FACTS_LARGE_FONT_PX),
        )

    /** The facts list in Spanish: the labels, the date and the language names. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun factsSpanish() =
        TitlePageScreen(
            TitlePageUiState.Ready(longMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = FACTS_PX),
        )

    /** Where to watch with both kinds: the Stream chip chosen, its providers' logos and names, and the JustWatch credit. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun whereToWatch() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = WATCH_PX),
        )

    /** A film that can only be bought: the one chip, and names longer than the column. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun whereToWatchBuyOnly() =
        TitlePageScreen(
            TitlePageUiState.Ready(buyOnlyMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = WATCH_PX),
        )

    /** The section at font scale 2, with the longest provider names. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun whereToWatchLargeFonts() =
        TitlePageScreen(
            TitlePageUiState.Ready(buyOnlyMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = WATCH_LARGE_FONT_PX),
        )

    /** The section in Spanish: the header, the chips and the credit. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun whereToWatchSpanish() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = WATCH_PX),
        )

    /** The collection rail, named by the collection, with a card in each state; no See all, since no page opens yet. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun collectionRail() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie(), fullExtras(), collectionParts = collectionFilms()),
            emptyFlow(),
            NO_COLLECTION_PAGE,
            scrollState = rememberScrollState(initial = COLLECTION_PX),
        )

    /** The rail with See all, under a long collection name, at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun collectionLargeFonts() =
        TitlePageScreen(
            TitlePageUiState.Ready(longCollectionMovie(), fullExtras(), collectionParts = collectionFilms()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = COLLECTION_LARGE_FONT_PX),
        )

    /** The rail in Spanish: the heading is the server's own collection name, and the status badges are translated. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun collectionSpanish() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie(), fullExtras(), collectionParts = collectionFilms()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = COLLECTION_PX),
        )
}
