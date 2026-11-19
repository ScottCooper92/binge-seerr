package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.ui.discover.request.ChoicesState
import com.cooper.binge.companion.seerr.ui.discover.request.DestinationPick
import com.cooper.binge.companion.seerr.ui.discover.request.QuotaState
import com.cooper.binge.companion.seerr.ui.discover.request.RequestProblem
import com.cooper.binge.companion.seerr.ui.discover.request.RequestProblemCause
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetActions
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetContent
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetTarget
import com.cooper.binge.companion.seerr.ui.discover.request.RequestSheetUiState
import com.cooper.binge.companion.seerr.ui.discover.request.ServersState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SHEET_TITLE = "The Lord of the Rings: The Fellowship of the Ring (Extended Director's Cut Special Edition)"
private const val SHEET_SERVER = "Radarr Ultra HD Remux Home Theatre Upstairs"
private const val SHEET_PROFILE = "Remux 2160p Dolby Vision HDR10+ Atmos"
private const val SHEET_FOLDER = "/mnt/storage/media/films/ultra-hd-remux/home-theatre"
private const val QUOTA_LIMIT = 100
private const val QUOTA_LEFT = 3
private const val QUOTA_DAYS = 30

private fun worstCase(): RequestSheetUiState.Ready {
    val server = RequestServer(1, SHEET_SERVER, is4k = true, isDefault = true, profileId = 1, rootFolder = SHEET_FOLDER)
    return RequestSheetUiState.Ready(
        target =
            RequestSheetTarget(
                TitleKind.Movie,
                tmdbId = 120,
                title = SHEET_TITLE,
                meta = "2001 · 2 h 58 min",
            ),
        access = RequestAccess(may4k = true, mayChooseDestination = true),
        quota = QuotaState.Known(RequestQuota(QUOTA_LIMIT, QUOTA_LEFT, QUOTA_DAYS)),
        servers = ServersState.Ready(listOf(server)),
        use4k = true,
        pick =
            DestinationPick(
                serverId = 1,
                profileId = 1,
                rootFolder = SHEET_FOLDER,
                choices = ChoicesState.Ready(RequestChoices(listOf(RequestChoice(1, SHEET_PROFILE)), listOf(SHEET_FOLDER))),
            ),
    )
}

/** Every read failed, and the last send got no answer, so the page shows each failure line and the red footer at once. */
private fun failedCase(): RequestSheetUiState.Ready =
    worstCase().copy(
        quota = QuotaState.Failed,
        pick = worstCase().pick?.copy(choices = ChoicesState.Failed),
        failure = RequestProblem(RequestProblemCause.Unreachable, null),
        footerFailed = true,
    )

private fun noActions() = RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

/** The request sheet at its worst: a long title, long server, profile and folder names, 4K on and a quota with a period, at 1.3 and 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequestSheetClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        fontScale: Float,
        state: RequestSheetUiState.Ready = worstCase(),
    ) = rule.assertNoClippingAt(fontScale) { RequestSheetContent(state, noActions()) }

    @Test fun `the request sheet at 1_3`() = check(FONT_SCALE_13)

    @Test fun `the request sheet at 2_0`() = check(FONT_SCALE_20)

    @Test fun `the failed request sheet at 1_3`() = check(FONT_SCALE_13, failedCase())

    @Test fun `the failed request sheet at 2_0`() = check(FONT_SCALE_20, failedCase())
}

/** The same sheet in Spanish, whose labels run longer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequestSheetSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) =
        rule.assertNoClippingAt(fontScale) {
            RequestSheetContent(
                worstCase().copy(quota = QuotaState.Known(RequestQuota(QUOTA_LIMIT, 0, QUOTA_DAYS))),
                noActions(),
            )
        }

    @Test fun `the request sheet at 1_3 in Spanish`() = check(FONT_SCALE_13)

    @Test fun `the request sheet at 2_0 in Spanish`() = check(FONT_SCALE_20)
}
