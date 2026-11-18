package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.request.RequestOptions
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetActions
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetContent
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetUiState
import com.cooper.binge.companion.seerr.ui.discover.title.SeriesPageScreen
import com.cooper.binge.companion.seerr.ui.discover.title.SeriesPageUiState
import com.cooper.binge.companion.seerr.ui.discover.title.TitlePageActions
import com.cooper.binge.companion.seerr.ui.discover.title.seriesFixture
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_SEASON = "The Extended Special Collector's Edition Season With A Very Long Name"
private const val SEASON_EPISODES = 24
private const val RATING_CRITICS = 83
private const val RATING_AUDIENCE = 85

private val STATUSES =
    listOf(
        TitleAvailability.Available,
        TitleAvailability.PartiallyAvailable,
        TitleAvailability.Processing,
        TitleAvailability.Pending,
        TitleAvailability.None,
    )

private fun seasons() =
    STATUSES.mapIndexed { index, status ->
        SeasonInfo(index + 1, if (index == 0) LONG_SEASON else "Season ${index + 1}", SEASON_EPISODES, "${2010 + index}", status, null)
    }

private fun worstCase(): SeriesPageUiState.Ready {
    val series =
        seriesFixture(seasons = seasons()).copy(
            tagline = "One ring to rule them all, one ring to find them, one ring to bring them all and in the darkness bind them",
            genres = listOf("Adventure", "Fantasy", "Action", "Epic Historical Drama"),
            networks = listOf("A Very Long Premium Cable Network", "Another Streaming Service Of Considerable Length"),
            creators = listOf("Christopher Montgomery-Fitzgerald", "Alexandria Wolfeschlegelsteinhausen"),
            cast = listOf(TitlePerson(1, "Elijah Wood-Montgomery-Fitzgerald", "Frodo Baggins of the Shire", null)),
        )
    val extras =
        TitleExtras(
            TitleRatings(RATING_CRITICS, RATING_AUDIENCE, null),
            listOf(TitleResult(TitleKind.Series, 2, "Related", "2003", null, null, null, null, TitleAvailability.None, null)),
            emptyList(),
        )
    return SeriesPageUiState.Ready(series, extras)
}

private fun sheetWorstCase(): RequestSheetUiState.Ready {
    val choices = seasons().map { SeasonChoice(it.number, it.name, it.episodeCount, it.standard) }
    return RequestSheetUiState.Ready(
        target =
            RequestSheetTarget(
                kind = TitleKind.Series,
                tmdbId = 1399,
                title = LONG_SEASON,
                seasonChoices = SeasonChoices(choices, choices),
            ),
        options = RequestOptions(may4k = false, servers = emptyList(), quota = null),
        pickedSeasons = setOf(2, 5),
    )
}

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
private val NO_SHEET_ACTIONS = RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {})

/** The series page with every season status, long names and every rating, at font scales 1.3 and 2.0, through the design system's clipping check. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeriesPageClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun page(fontScale: Float) = rule.assertNoClippingAt(fontScale) { SeriesPageScreen(worstCase(), emptyFlow(), NO_ACTIONS) }

    private fun sheet(fontScale: Float) = rule.assertNoClippingAt(fontScale) { RequestSheetContent(sheetWorstCase(), NO_SHEET_ACTIONS) }

    @Test fun `the series page at 1_3`() = page(FONT_SCALE_13)

    @Test fun `the series page at 2_0`() = page(FONT_SCALE_20)

    @Test fun `the season step at 1_3`() = sheet(FONT_SCALE_13)

    @Test fun `the season step at 2_0`() = sheet(FONT_SCALE_20)
}

/** The same page and sheet in Spanish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeriesPageSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun page(fontScale: Float) = rule.assertNoClippingAt(fontScale) { SeriesPageScreen(worstCase(), emptyFlow(), NO_ACTIONS) }

    private fun sheet(fontScale: Float) = rule.assertNoClippingAt(fontScale) { RequestSheetContent(sheetWorstCase(), NO_SHEET_ACTIONS) }

    @Test fun `the series page at 1_3 in Spanish`() = page(FONT_SCALE_13)

    @Test fun `the series page at 2_0 in Spanish`() = page(FONT_SCALE_20)

    @Test fun `the season step at 1_3 in Spanish`() = sheet(FONT_SCALE_13)

    @Test fun `the season step at 2_0 in Spanish`() = sheet(FONT_SCALE_20)
}
