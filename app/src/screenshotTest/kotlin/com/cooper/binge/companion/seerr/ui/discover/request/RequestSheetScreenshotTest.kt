package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.BingeMessageDialogContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrExpandedFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrExpandedSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame

private val SHORT_HEIGHT = 520.dp
private val FORM_HEIGHT = 700.dp
private val WORST_HEIGHT = 1100.dp
private const val QUOTA_LIMIT = 10
private const val QUOTA_DAYS = 7
private const val QUOTA_LEFT = 3

private val STANDARD = RequestServer(1, "Radarr", is4k = false, isDefault = true, profileId = 4, rootFolder = "/media/movies")
private val UHD = RequestServer(2, "Radarr 4K", is4k = true, isDefault = true, profileId = 7, rootFolder = "/media/movies-4k")
private val CHOICES = RequestChoices(listOf(RequestChoice(4, "HD-1080p"), RequestChoice(5, "Any")), listOf("/media/movies", "/media/more"))
private val OPEN_QUOTA = QuotaState.Known(RequestQuota(QUOTA_LIMIT, QUOTA_LEFT, QUOTA_DAYS))

private fun target(title: String = "The Matrix") =
    RequestSheetTarget(TitleKind.Movie, tmdbId = 603, title = title, meta = "1999 · 2 h 16 min")

private fun plain() =
    RequestSheetUiState.Ready(target(), RequestAccess(may4k = false, mayChooseDestination = false), quota = QuotaState.Known(null))

private fun advanced(
    quota: QuotaState = OPEN_QUOTA,
    servers: ServersState = ServersState.Ready(listOf(STANDARD, UHD)),
    choices: ChoicesState = ChoicesState.Ready(CHOICES),
) = RequestSheetUiState.Ready(
    target = target(),
    access = RequestAccess(may4k = true, mayChooseDestination = true),
    quota = quota,
    servers = servers,
    pick =
        DestinationPick(serverId = 1, profileId = 4, rootFolder = "/media/movies", choices = choices).takeIf {
            servers is ServersState.Ready
        },
)

private fun admin(state: RequestSheetUiState.Ready) =
    state.copy(access = state.access.copy(mayManageServers = true), serverUrls = mapOf(1 to "https://radarr.example.com"))

private fun worstCase() =
    advanced().copy(
        target = target("The Lord of the Rings: The Fellowship of the Ring (Extended Director's Cut Special Edition)"),
        servers =
            ServersState.Ready(
                listOf(UHD.copy(name = "Radarr Ultra HD Remux Home Theatre Upstairs", rootFolder = "/mnt/storage/media/films/ultra-hd")),
            ),
        use4k = true,
        pick =
            DestinationPick(
                serverId = 2,
                profileId = 5,
                rootFolder = "/mnt/storage/media/films/ultra-hd",
                choices =
                    ChoicesState.Ready(
                        RequestChoices(
                            listOf(RequestChoice(5, "Remux 2160p Dolby Vision HDR10+ Atmos")),
                            listOf("/mnt/storage/media/films/ultra-hd"),
                        ),
                    ),
            ),
    )

private fun failedSend() = RequestProblem(RequestProblemCause.Unreachable, null)

/** The request sheet as the 420dp end-edge side sheet of a foldable and a tablet, at 1x, 1.3x and 2x text and in Spanish. */
class RequestSheetSideScreenshotTest {
    @PreviewTest
    @SeerrExpandedFontScalePreviews
    @Composable
    fun advancedFontScales() = SideFrame(advanced())

    @PreviewTest
    @SeerrExpandedFontScalePreviews
    @Composable
    fun worstCaseFontScales() = SideFrame(worstCase())

    @PreviewTest
    @SeerrExpandedSpanishPreviews
    @Composable
    fun advancedSpanish() = SideFrame(advanced())
}

/** The in-app request sheet for a film: the header, the options group with its destination rows, each failure, and the dialogs. */
class RequestSheetScreenshotTest {
    /** A user who may only request: no 4K, no destination, no quota to show. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun plainUser() = Frame(plain(), SHORT_HEIGHT)

    /** A user who may choose a destination: server, profile and root folder sit in the options group. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun advancedUser() = Frame(advanced(), FORM_HEIGHT)

    /** An administrator with a server chosen: Edit server and Open Radarr follow the destination rows. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun adminEditServer() = Frame(admin(advanced()), FORM_HEIGHT, adminActions = true)

    /** An administrator where the server holds none of this version: Add a server, and no destination rows. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun adminAddServer() = Frame(admin(advanced(servers = ServersState.Ready(listOf(UHD)))), FORM_HEIGHT, adminActions = true)

    /** While the lists are still being read: spinners in the rows, and the button held. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun advancedLoading() = Frame(advanced(choices = ChoicesState.Loading), FORM_HEIGHT)

    /** The server's profiles and folders failed to load: one line with a tap to try again. The button stays enabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun choicesFailed() = Frame(advanced(choices = ChoicesState.Failed), FORM_HEIGHT)

    /** The list of servers failed to load. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun serversFailed() = Frame(advanced(servers = ServersState.Failed), FORM_HEIGHT)

    /** The quota could not be read: the row says so, and the button stays enabled. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quotaFailed() = Frame(advanced(quota = QuotaState.Failed), FORM_HEIGHT)

    /** Nothing left in the quota: the button is held and the row says why. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quotaSpent() = Frame(advanced(quota = QuotaState.Known(RequestQuota(QUOTA_LIMIT, 0, QUOTA_DAYS))), FORM_HEIGHT)

    /** The send is in flight: the button spins and the rows lock. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sending() = Frame(advanced().copy(submitting = true), FORM_HEIGHT)

    /** A send that got no answer: the button is red with a short reason for a few seconds. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun footerFailed() = Frame(advanced().copy(failure = failedSend(), footerFailed = true), FORM_HEIGHT)

    /** A refusal: what happened, the cause and next step, then the server's own words, and one OK. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun refusalDialog() =
        BingeMessageDialogContent(
            title = stringResource(R.string.request_problem_title),
            message = stringResource(R.string.request_problem_quota) + "\n\nRequest quota exceeded",
            onDismiss = {},
        )

    /** A tap on the red button: the full reason, and a choice to try again. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun failureDialog() =
        BingeConfirmDialogContent(
            title = stringResource(R.string.request_problem_title),
            message = stringResource(R.string.request_problem_unreachable),
            confirmLabel = stringResource(R.string.action_try_again),
            onConfirm = {},
            onDismissRequest = {},
        )

    /** The longest names, every failure at once and the red button, at font scale 1.3. */
    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun worstCaseFont13() = Frame(worstCase(), WORST_HEIGHT)

    /** The longest names at font scale 2, with the quota failed and the button red. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun worstCaseLargeFonts() =
        Frame(worstCase().copy(quota = QuotaState.Failed, failure = failedSend(), footerFailed = true), WORST_HEIGHT)

    /** The same sheet in Spanish, whose labels run longer, with a spent quota. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun worstCaseSpanish() = Frame(worstCase().copy(quota = QuotaState.Known(RequestQuota(QUOTA_LIMIT, 0, QUOTA_DAYS))), WORST_HEIGHT)
}

@Composable
private fun SideFrame(state: RequestSheetUiState) {
    SideSheetFrame { RequestSheetContent(state, RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})) }
}

@Composable
private fun Frame(
    state: RequestSheetUiState,
    height: Dp,
    adminActions: Boolean = false,
) {
    SheetFrame(Modifier.height(height)) {
        RequestSheetContent(
            state,
            RequestSheetActions(
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                {},
                onEditServer = { _: Int -> }.takeIf { adminActions },
                onAddServer = {}.takeIf { adminActions },
                onOpenServer = { _: String -> }.takeIf { adminActions },
            ),
        )
    }
}
