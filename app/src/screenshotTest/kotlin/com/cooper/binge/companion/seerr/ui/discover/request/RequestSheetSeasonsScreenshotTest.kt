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
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame

private val FORM_HEIGHT = 640.dp
private val PAGE_HEIGHT = 720.dp
private val WORST_HEIGHT = 1200.dp
private const val EPISODES = 10
private const val QUOTA_LIMIT = 10
private const val QUOTA_DAYS = 7
private const val LONG_NAME = "The Extended Special Collector's Edition Season With A Very Long Name"

private fun choices(
    longName: Boolean = false,
    singleOpen: Boolean = false,
) = if (singleOpen) {
    listOf(
        SeasonChoice(1, "Season 1", EPISODES, TitleAvailability.Available),
        SeasonChoice(2, "Season 2", EPISODES, TitleAvailability.Processing),
        SeasonChoice(3, "Season 3", EPISODES, TitleAvailability.Pending),
        SeasonChoice(4, "Season 4", EPISODES, TitleAvailability.None),
    )
} else {
    listOf(
        SeasonChoice(1, if (longName) LONG_NAME else "Season 1", EPISODES, TitleAvailability.Available),
        SeasonChoice(2, "Season 2", EPISODES, TitleAvailability.Processing),
        SeasonChoice(3, "Season 3", EPISODES, TitleAvailability.Pending),
        SeasonChoice(4, "Season 4", EPISODES, TitleAvailability.None),
        SeasonChoice(5, "Season 5", EPISODES, TitleAvailability.None),
    )
}

private fun state(
    picked: Set<Int> = emptySet(),
    partial: Boolean = true,
    longName: Boolean = false,
    quota: RequestQuota? = null,
    singleOpen: Boolean = false,
) = RequestSheetUiState.Ready(
    target =
        RequestSheetTarget(
            kind = TitleKind.Series,
            tmdbId = 1399,
            title = "The Long Winter",
            seasonChoices = SeasonChoices(choices(longName, singleOpen), choices(longName, singleOpen)),
            partialRequests = partial,
            meta = "2011 · 5 seasons",
        ),
    access = RequestAccess(may4k = false, mayChooseDestination = false),
    quota = QuotaState.Known(quota),
    pickedSeasons = picked,
)

private fun noActions() = RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun Form(
    state: RequestSheetUiState.Ready,
    height: Dp = FORM_HEIGHT,
) {
    SheetFrame(Modifier.height(height)) { RequestSheetContent(state, noActions()) }
}

@Composable
private fun SeasonPage(
    state: RequestSheetUiState.Ready,
    height: Dp = PAGE_HEIGHT,
) {
    SheetFrame(
        Modifier.height(height),
    ) { RequestSeasonPage(checkNotNull(state.seasons), enabled = true, actions = noActions(), onBack = {}) }
}

/** The request sheet for a show: the form with its Seasons row, and the season page it opens. */
class RequestSheetSeasonsScreenshotTest {
    /** Nothing picked yet: the Seasons row is unticked and the button is held. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formNonePicked() = Form(state())

    /** Some open seasons picked: the row's box shows a dash. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formOnePicked() = Form(state(picked = setOf(4)))

    /** Every open season picked: the box is ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formAllPicked() = Form(state(picked = setOf(4, 5)))

    /** One season left to pick: the Seasons row names it, with its own box and no chevron, in the multi-season row's place. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formSingleSeason() = Form(state(singleOpen = true))

    /** The same season ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun formSingleSeasonPicked() = Form(state(picked = setOf(4), singleOpen = true))

    /** The single-season row at font scale 2 and in Spanish. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun formSingleSeasonLargeFonts() = Form(state(singleOpen = true, longName = true), WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun formSingleSeasonSpanish() = Form(state(picked = setOf(4), singleOpen = true), WORST_HEIGHT)

    /** Nothing picked yet on the season page: the covered seasons are shown and cannot be picked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun nonePicked() = SeasonPage(state())

    /** One open season picked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun onePicked() = SeasonPage(state(picked = setOf(4)))

    /** Every open season picked, so All seasons is ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun allPicked() = SeasonPage(state(picked = setOf(4, 5)))

    /** The server takes the whole show: no choice, and the page says what will be sent. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun partialOff() = SeasonPage(state(partial = false))

    /** The quota has room for one more season: the other open season and All seasons cannot be picked, and the note says why. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quotaLimited() = SeasonPage(state(picked = setOf(4), quota = RequestQuota(QUOTA_LIMIT, 1, QUOTA_DAYS)))

    /** The server takes the whole show but the quota cannot: the page says so and the button on the form is held. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quotaTooSmall() = SeasonPage(state(partial = false, quota = RequestQuota(QUOTA_LIMIT, 1, QUOTA_DAYS)))

    /** A long season name on the season page at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = SeasonPage(state(picked = setOf(4), longName = true), WORST_HEIGHT)

    /** The form for a show at font scale 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun formLargeFonts() = Form(state(picked = setOf(4), longName = true, quota = RequestQuota(QUOTA_LIMIT, 1, QUOTA_DAYS)), WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = SeasonPage(state(picked = setOf(4), longName = true), WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun formSpanish() = Form(state(picked = setOf(4), longName = true, quota = RequestQuota(QUOTA_LIMIT, 1, QUOTA_DAYS)), WORST_HEIGHT)
}
