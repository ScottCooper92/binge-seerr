package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.TitleRequest
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.preview.SeerrExpandedFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrExpandedSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrNarrowTabletPreview
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import kotlinx.coroutines.flow.emptyFlow

private const val SCROLLED_PX = 2500

/** Scrolled past the end: the state clamps it, so the page rests on its last section, the Details. */
private const val DETAILS_END_PX = 100_000
private const val WATCH_PX = 2900
private const val WATCH_LARGE_FONT_PX = 5200
private const val COLLECTION_PX = 3800
private const val COLLECTION_LARGE_FONT_PX = 6900

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onReport = {})

/** As [NO_ACTIONS], but a studio opens its grid, so the studios are links in the accent colour. */
private val LINKED_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onOpenCompany = {}, onReport = {})

/** As [NO_ACTIONS], but a keyword opens its grid, so the keywords are chips. */
private val KEYWORD_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, onOpenKeyword = {})

/** As [NO_ACTIONS], but no page opens for a collection, as on the phone today: the rail has no See all. */
private val NO_COLLECTION_PAGE = TitlePageActions({}, {}, {}, {}, {}, {}, {}, null, {}, {})

/** As [NO_ACTIONS], but the viewer may block, so Block is among the round buttons. */
private val BLOCK_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, block = TitleBlock({}, {}, {}))

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

    /** Already in the library, with no request on file and no version open: Watch is the primary, and Report joins Trailer. */
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

    /** In the library, but the server gave no link and no player answers: the status primary stands in for Watch, and Manage is not repeated. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun availableNoWatchLink() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Available, mayRequest = false), fourK = null, watchUrl = null),
                fullExtras(),
            ),
        )

    /** Available to a viewer the server takes no issue from: Trailer is there and Report is not. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun availableNoReport() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Available, mayRequest = false), fourK = null).copy(mayReport = false),
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

    /** Requested, waiting on approval: the primary says so and opens that request. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun requested() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Pending, mayRequest = false), fourK = null)
                    .copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Pending, 5))),
                fullExtras(),
            ),
        )

    /** Approved and being fetched. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun processing() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Processing, mayRequest = false), fourK = null)
                    .copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Approved, 5))),
                fullExtras(),
            ),
        )

    /** Blocklisted: the primary is drawn and disabled. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun blocklisted() =
        Frame(TitlePageUiState.Ready(fullMovie(standard = VersionState(TitleAvailability.Blocklisted, mayRequest = false), fourK = null)))

    /** Every request was declined and nothing else is held: the primary reads Declined and is tonal, and Block joins Trailer. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun declined() =
        TitlePageScreen(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.None, mayRequest = true), fourK = null)
                    .copy(requests = listOf(TitleRequest(7, false, SeerrRequestStatusCode.Declined, 5)), mayBlock = true),
                fullExtras(),
            ),
            emptyFlow(),
            BLOCK_ACTIONS,
        )

    /** Partly available with 4K still open: the primary says so with its dot, and Report joins Trailer. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun partlyAvailable() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(
                    standard = VersionState(TitleAvailability.PartiallyAvailable, mayRequest = false),
                    fourK = VersionState(TitleAvailability.None, mayRequest = true),
                ),
                fullExtras(),
            ),
        )

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

    /** An available title on a server with a watchlist, the title not on it: an outlined bookmark labelled Watchlist sits beside Trailer, before Report. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun watchlistOff() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Available, mayRequest = false), fourK = null).copy(mayWatchlist = true),
                fullExtras(),
            ),
        )

    /** The same title on the watchlist: the bookmark is filled. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun watchlistOn() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Available, mayRequest = false), fourK = null)
                    .copy(mayWatchlist = true, onWatchlist = true),
                fullExtras(),
            ),
        )

    /** The action row at font scale 2 with all four buttons, the primary and Trailer, Watchlist and Report: the labels stay whole. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun watchlistLargeFonts() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Available, mayRequest = false), fourK = null).copy(mayWatchlist = true),
                fullExtras(),
            ),
        )

    /** An available title at font scale 2: Trailer and Report, both labelled, sit beside the primary and the labels stay whole. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun availableLargeFonts() =
        Frame(
            TitlePageUiState.Ready(
                fullMovie(standard = VersionState(TitleAvailability.Available, mayRequest = false), fourK = null),
                fullExtras(),
            ),
        )

    /** The longest title, tagline and names at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(TitlePageUiState.Ready(longMovie(), fullExtras()))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(TitlePageUiState.Ready(longMovie(), fullExtras()))

    /** The Details at the end of the page at font scale 2, with the longest studio names: a label and its value stay readable. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun factsLargeFonts() =
        TitlePageScreen(
            TitlePageUiState.Ready(longMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = DETAILS_END_PX),
        )

    /** A tablet in landscape: the Details run in three columns across the width, under the rails. */
    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun detailsTablet() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = DETAILS_END_PX),
        )

    /** The Details with the studios as links: each name in the accent colour, where a tap opens that studio's grid. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun factsLinked() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie().copy(companiesOpen = true), fullExtras()),
            emptyFlow(),
            LINKED_ACTIONS,
            scrollState = rememberScrollState(initial = DETAILS_END_PX),
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
            scrollState = rememberScrollState(initial = DETAILS_END_PX),
        )

    /** The keywords as chips, where a tap opens that keyword's grid. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun keywordsLinked() =
        TitlePageScreen(
            TitlePageUiState.Ready(fullMovie().copy(keywordsOpen = true), fullExtras()),
            emptyFlow(),
            KEYWORD_ACTIONS,
            scrollState = rememberScrollState(initial = DETAILS_END_PX),
        )

    /** The Details in Spanish: the heading, the labels, the date and the language names. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun factsSpanish() =
        TitlePageScreen(
            TitlePageUiState.Ready(longMovie(), fullExtras()),
            emptyFlow(),
            NO_ACTIONS,
            scrollState = rememberScrollState(initial = DETAILS_END_PX),
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

    /** A failed read: this app's headline and message, and Try again. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = Frame(TitlePageUiState.Error(SeerrError.Unreachable))

    /** A server Android will not let this app reach: this app's headline, and Allow in place of Try again. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failedLocalNetworkDenied() = Frame(TitlePageUiState.Error(SeerrError.LocalNetworkDenied))
}
