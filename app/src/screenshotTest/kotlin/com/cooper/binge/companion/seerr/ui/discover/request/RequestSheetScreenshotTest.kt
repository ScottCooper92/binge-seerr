package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeMessageDialogContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestChoice
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestOptions
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SeerrFontScale13Previews
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame

private val PLAIN_HEIGHT = 420.dp
private val ADVANCED_HEIGHT = 640.dp
private val WORST_HEIGHT = 760.dp
private const val QUOTA_LIMIT = 10
private const val QUOTA_DAYS = 7
private const val QUOTA_LEFT = 3

private val STANDARD = RequestServer(1, "Radarr", is4k = false, isDefault = true, profileId = 4, rootFolder = "/media/movies")
private val UHD = RequestServer(2, "Radarr 4K", is4k = true, isDefault = true, profileId = 7, rootFolder = "/media/movies-4k")
private val CHOICES = RequestChoices(listOf(RequestChoice(4, "HD-1080p"), RequestChoice(5, "Any")), listOf("/media/movies", "/media/more"))

private fun target(title: String = "The Matrix") = RequestSheetTarget(TitleKind.Movie, tmdbId = 603, title = title)

private fun plain() = RequestSheetUiState.Ready(target(), RequestOptions(may4k = false, servers = emptyList(), quota = null))

private fun advanced(quota: RequestQuota? = RequestQuota(QUOTA_LIMIT, QUOTA_LEFT, QUOTA_DAYS)) =
    RequestSheetUiState.Ready(
        target = target(),
        options = RequestOptions(may4k = true, servers = listOf(STANDARD, UHD), quota = quota),
        pick = DestinationPick(serverId = 1, profileId = 4, rootFolder = "/media/movies", choices = CHOICES),
    )

private fun worstCase(quota: RequestQuota?) =
    RequestSheetUiState.Ready(
        target = target("The Lord of the Rings: The Fellowship of the Ring (Extended Director's Cut Special Edition)"),
        options =
            RequestOptions(
                may4k = true,
                servers =
                    listOf(
                        UHD.copy(name = "Radarr Ultra HD Remux Home Theatre Upstairs", rootFolder = "/mnt/storage/media/films/ultra-hd"),
                    ),
                quota = quota,
            ),
        use4k = true,
        pick =
            DestinationPick(
                serverId = 2,
                profileId = 5,
                rootFolder = "/mnt/storage/media/films/ultra-hd",
                choices =
                    RequestChoices(
                        listOf(RequestChoice(5, "Remux 2160p Dolby Vision HDR10+ Atmos")),
                        listOf("/mnt/storage/media/films/ultra-hd"),
                    ),
            ),
    )

/** The in-app request sheet: what a request-only user sees, what an advanced user sees, a spent quota, and the dialog a refusal opens. */
class RequestSheetScreenshotTest {
    /** A user who may only request: no 4K, no destination, no quota to show. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun plainUser() = Frame(plain(), PLAIN_HEIGHT)

    /** An advanced user: the 4K switch, the server, profile and folder, and the quota left. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun advancedUser() = Frame(advanced(), ADVANCED_HEIGHT)

    /** Nothing left in the quota: the button is held and the line says why. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun quotaSpent() = Frame(advanced(RequestQuota(QUOTA_LIMIT, 0, QUOTA_DAYS)), ADVANCED_HEIGHT)

    /** The send is in flight: the button spins and the fields lock. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sending() = Frame(advanced().copy(submitting = true), ADVANCED_HEIGHT)

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

    /** The longest names at font scale 1.3. */
    @PreviewTest
    @SeerrFontScale13Previews
    @Composable
    fun worstCaseFont13() = Frame(worstCase(RequestQuota(QUOTA_LIMIT, QUOTA_LEFT, QUOTA_DAYS)), WORST_HEIGHT)

    /** The longest names at font scales 1.5 and 2. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun worstCaseLargeFonts() = Frame(worstCase(RequestQuota(QUOTA_LIMIT, 0, QUOTA_DAYS)), WORST_HEIGHT)

    /** The same sheet in Spanish, whose labels run longer, with a spent quota. */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun worstCaseSpanish() = Frame(worstCase(RequestQuota(QUOTA_LIMIT, 0, QUOTA_DAYS)), WORST_HEIGHT)
}

@Composable
private fun Frame(
    state: RequestSheetUiState,
    height: Dp,
) {
    SheetFrame(Modifier.height(height)) {
        RequestSheetContent(state, RequestSheetActions({}, {}, {}, {}, {}, {}, {}, {}, {}))
    }
}
