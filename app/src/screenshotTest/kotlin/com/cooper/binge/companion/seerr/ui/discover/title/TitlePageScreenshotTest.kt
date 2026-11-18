package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.title.VersionState
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import kotlinx.coroutines.flow.emptyFlow

private const val SCROLLED_PX = 2500
private const val FACTS_PX = 1400
private const val COLLECTION_PX = 3300
private const val COLLECTION_LARGE_FONT_PX = 6200
private const val FACTS_LARGE_FONT_PX = 2700

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

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

    /** The longest title, tagline and names at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(TitlePageUiState.Ready(longMovie(), fullExtras()))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(TitlePageUiState.Ready(longMovie(), fullExtras()))

    /** The facts list at font scales 1.5 and 2, with the longest studio names: a label and its value stay readable. */
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

    /** The rail with See all, under a long collection name, at font scales 1.5 and 2. */
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
