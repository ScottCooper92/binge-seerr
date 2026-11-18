package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.ui.discover.request.QuotaState
import com.cooper.binge.companion.seerr.ui.discover.request.RequestFilm
import com.cooper.binge.companion.seerr.ui.discover.request.RequestFilmsPage
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetActions
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetContent
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val QUOTA_LIMIT = 10
private const val QUOTA_DAYS = 7
private val STATUSES =
    listOf(
        TitleAvailability.None,
        TitleAvailability.Available,
        TitleAvailability.PartiallyAvailable,
        TitleAvailability.Processing,
        TitleAvailability.Pending,
        TitleAvailability.Blocklisted,
    )

private fun filmsCase(): RequestSheetUiState.Ready =
    RequestSheetUiState.Ready(
        target =
            RequestSheetTarget(
                kind = TitleKind.Movie,
                tmdbId = 2344,
                title = "The Extended Special Collector's Edition Trilogy Of Considerable Length",
                fourKOpen = false,
                meta = "6 films",
                films =
                    STATUSES.mapIndexed { index, status ->
                        RequestFilm(
                            index + 1,
                            "The Extraordinarily Long Title Of Film Number ${index + 1} And Its Subtitle",
                            "${2000 + index}",
                            status,
                        )
                    },
            ),
        access = RequestAccess(may4k = false, mayChooseDestination = false),
        quota = QuotaState.Known(RequestQuota(QUOTA_LIMIT, 3, QUOTA_DAYS)),
    )

private fun noActions() = RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

/** The collection request sheet, form and films page, with every film status and the longest names, at font scales 1.3 and 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequestSheetFilmsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun form(fontScale: Float) = rule.assertNoClippingAt(fontScale) { RequestSheetContent(filmsCase(), noActions()) }

    private fun page(fontScale: Float) =
        rule.assertNoClippingAt(fontScale) {
            val state = filmsCase()
            RequestFilmsPage(state.target.films, checkNotNull(state.films), enabled = true, actions = noActions(), onBack = {})
        }

    @Test fun `the films form at 1_3`() = form(FONT_SCALE_13)

    @Test fun `the films form at 2_0`() = form(FONT_SCALE_20)

    @Test fun `the films page at 1_3`() = page(FONT_SCALE_13)

    @Test fun `the films page at 2_0`() = page(FONT_SCALE_20)
}

/** The same sheet in Spanish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequestSheetFilmsSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun form(fontScale: Float) = rule.assertNoClippingAt(fontScale) { RequestSheetContent(filmsCase(), noActions()) }

    @Test fun `the films form at 1_3 in Spanish`() = form(FONT_SCALE_13)

    @Test fun `the films form at 2_0 in Spanish`() = form(FONT_SCALE_20)
}
