package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame

private val FORM_HEIGHT = 640.dp
private val PAGE_HEIGHT = 720.dp
private val WORST_HEIGHT = 1100.dp
private const val QUOTA_LIMIT = 10
private const val QUOTA_DAYS = 7
private const val LONG_TITLE = "The Extended Special Collector's Edition Trilogy Of Considerable Length"

private fun films(longNames: Boolean) =
    listOf(
        RequestFilm(
            1,
            if (longNames) "The Very Long Title Of Film Number One And Its Subtitle" else "The Matrix",
            "1999",
            TitleAvailability.Available,
        ),
        RequestFilm(2, "The Matrix Reloaded", "2003", TitleAvailability.Processing),
        RequestFilm(3, "The Matrix Revolutions", "2003", TitleAvailability.None),
        RequestFilm(4, "The Matrix Resurrections", "2021", TitleAvailability.None),
        RequestFilm(5, "The Matrix Origins", "2026", TitleAvailability.None),
    )

private fun state(
    picked: Set<Int>? = null,
    remaining: Int = QUOTA_LIMIT,
    longNames: Boolean = false,
) = RequestSheetUiState.Ready(
    target =
        RequestSheetTarget(
            kind = TitleKind.Movie,
            tmdbId = 2344,
            title = if (longNames) LONG_TITLE else "The Matrix Collection",
            fourKOpen = false,
            meta = "5 films",
            films = films(longNames),
        ),
    access = RequestAccess(may4k = false, mayChooseDestination = false),
    quota = QuotaState.Known(RequestQuota(QUOTA_LIMIT, remaining, QUOTA_DAYS)),
    pickedFilms = picked,
)

private fun noActions() = RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun Form(
    state: RequestSheetUiState.Ready,
    height: Dp = FORM_HEIGHT,
) {
    SheetFrame(Modifier.height(height)) { RequestSheetContent(state, noActions()) }
}

@Composable
private fun FilmsPage(
    state: RequestSheetUiState.Ready,
    height: Dp = PAGE_HEIGHT,
) {
    SheetFrame(Modifier.height(height)) {
        RequestFilmsPage(state.target.films, checkNotNull(state.films), enabled = true, actions = noActions(), onBack = {})
    }
}

/** The request sheet for a collection: the form with its Films row and the button naming how many, and the films page it opens. */
class RequestSheetFilmsScreenshotTest {
    /** The quota covers every open film, so all three are ticked before the user touches anything. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formAllTicked() = Form(state())

    /** The quota has room for fewer than the open films, so none is ticked and the button is held. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formNoneTicked() = Form(state(remaining = 2))

    /** One film ticked: the row's box shows a dash. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formOneTicked() = Form(state(picked = setOf(3), remaining = 2))

    /** The films page with everything open ticked: the held films are shown with their status and cannot be ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun pageAllTicked() = FilmsPage(state())

    /** The films page under a quota that caps the ticks, with its note. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun pageCapped() = FilmsPage(state(picked = setOf(3, 4), remaining = 2))

    /** The longest names at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun formLargeFonts() = Form(state(longNames = true), WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun formSpanish() = Form(state(longNames = true), WORST_HEIGHT)
}
