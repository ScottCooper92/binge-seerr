package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import kotlinx.coroutines.flow.emptyFlow

private val NO_ACTIONS = CollectionPageActions({}, {}, {}, {}, {}, {})

@Composable
private fun Frame(state: CollectionPageUiState) = CollectionPageScreen(state, emptyFlow(), NO_ACTIONS)

/** The collection page: the layout once across the device matrix, then each state of it on the phone. */
class CollectionPageScreenshotTest {
    /** A film in each state the server can hold it in, so one is still left to request. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun mixed() = Frame(CollectionPageUiState.Ready(mixedCollection()))

    /** A viewer who may block collections: the overflow sits beside back. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun blockable() = Frame(CollectionPageUiState.Ready(mixedCollection(), mayBlock = true))

    /** Every film blocklisted, so the collection reads as blocked. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun blocked() = Frame(CollectionPageUiState.Ready(blockedCollection(), mayBlock = true))

    /** Every film already on the server: the count says no more to request. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun allAvailable() = Frame(CollectionPageUiState.Ready(allAvailableCollection()))

    /** The server gave no story and no synopses: the sections it left empty are left out. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun sparse() = Frame(CollectionPageUiState.Ready(sparseCollection()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(CollectionPageUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun error() = Frame(CollectionPageUiState.Error(SeerrError.Unreachable))

    /** The longest names at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(CollectionPageUiState.Ready(longCollection()))

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(CollectionPageUiState.Ready(longCollection()))
}
