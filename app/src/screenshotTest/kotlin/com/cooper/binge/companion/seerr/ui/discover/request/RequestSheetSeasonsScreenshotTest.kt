package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestOptions
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame

private const val SEASONS_HEIGHT = 720
private const val EPISODES = 10
private const val QUOTA_LIMIT = 10
private const val QUOTA_DAYS = 7
private const val LONG_NAME = "The Extended Special Collector's Edition Season With A Very Long Name"

private fun choices(longName: Boolean = false) =
    listOf(
        SeasonChoice(1, if (longName) LONG_NAME else "Season 1", EPISODES, TitleAvailability.Available),
        SeasonChoice(2, "Season 2", EPISODES, TitleAvailability.Processing),
        SeasonChoice(3, "Season 3", EPISODES, TitleAvailability.Pending),
        SeasonChoice(4, "Season 4", EPISODES, TitleAvailability.None),
        SeasonChoice(5, "Season 5", EPISODES, TitleAvailability.None),
    )

private fun state(
    picked: Set<Int> = emptySet(),
    partial: Boolean = true,
    longName: Boolean = false,
    quota: RequestQuota? = null,
) = RequestSheetUiState.Ready(
    target =
        RequestSheetTarget(
            kind = TitleKind.Series,
            tmdbId = 1399,
            title = "The Long Winter",
            seasonChoices = SeasonChoices(choices(longName), choices(longName)),
            partialRequests = partial,
        ),
    options = RequestOptions(may4k = false, servers = emptyList(), quota = quota),
    pickedSeasons = picked,
)

@Composable
private fun Frame(
    state: RequestSheetUiState,
    height: Dp = SEASONS_HEIGHT.dp,
) {
    SheetFrame(
        androidx.compose.ui.Modifier
            .height(height),
    ) {
        RequestSheetContent(state, RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}))
    }
}

/** The request sheet for a show: its seasons, with the covered ones shown as such and only the open ones pickable. */
class RequestSheetSeasonsScreenshotTest {
    /** Nothing picked yet: the button is disabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun nonePicked() = Frame(state())

    /** One open season picked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun onePicked() = Frame(state(picked = setOf(4)))

    /** Every open season picked, so All seasons is ticked. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun allPicked() = Frame(state(picked = setOf(4, 5)))

    /** The server takes the whole show: no choice, and the sheet says what will be sent. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun partialOff() = Frame(state(partial = false))

    /** The quota has room for one more season: the other open season and All seasons cannot be picked, and the note says why. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quotaLimited() = Frame(state(picked = setOf(4), quota = RequestQuota(QUOTA_LIMIT, 1, QUOTA_DAYS)))

    /** The server takes the whole show but the quota cannot: the sheet says so and the button is disabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quotaTooSmall() = Frame(state(partial = false, quota = RequestQuota(QUOTA_LIMIT, 1, QUOTA_DAYS)))

    /** A long season name at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun longLargeFonts() = Frame(state(picked = setOf(4), longName = true), WORST_HEIGHT)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun longSpanish() = Frame(state(picked = setOf(4), longName = true), WORST_HEIGHT)
}

private val WORST_HEIGHT = 1200.dp
