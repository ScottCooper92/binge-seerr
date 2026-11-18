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

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {})

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
}
