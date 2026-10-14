package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvSideSheetPanel
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.AdvancedRequestError
import com.cooper.binge.companion.seerr.ui.AdvancedRequestUiState
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import com.cooper.binge.companion.seerr.ui.LinkFlow
import com.cooper.binge.companion.seerr.ui.SetupError
import com.cooper.binge.companion.seerr.ui.SetupNotice
import com.cooper.binge.companion.seerr.ui.SetupServer
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.SignInForm
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.commonIssueDetail
import com.cooper.binge.companion.seerr.ui.requests.DetailDownload
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDestination
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.ServerService
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.tv.NoAdvancedActions
import com.cooper.binge.companion.seerr.ui.tv.NoSetupActions
import com.cooper.binge.companion.seerr.ui.tv.TvAdvancedRequestScreen
import com.cooper.binge.companion.seerr.ui.tv.TvConsentScreen
import com.cooper.binge.companion.seerr.ui.tv.TvSeerrErrorPage
import com.cooper.binge.companion.seerr.ui.tv.TvSetupLinkPlate
import com.cooper.binge.companion.seerr.ui.tv.TvSetupScreen
import com.cooper.binge.companion.seerr.ui.tv.issues.ISSUE_PAGE_PHONE
import com.cooper.binge.companion.seerr.ui.tv.issues.ISSUE_PAGE_THREAD
import com.cooper.binge.companion.seerr.ui.tv.issues.KEY_REPORT
import com.cooper.binge.companion.seerr.ui.tv.issues.NoIssueSheetActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueSheet
import com.cooper.binge.companion.seerr.ui.tv.issues.issueCardPage
import com.cooper.binge.companion.seerr.ui.tv.requests.DetailSheet
import com.cooper.binge.companion.seerr.ui.tv.requests.TvMarkAsSteps
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.settings.PAGE_BUG_REPORT
import com.cooper.binge.companion.seerr.ui.tv.settings.PAGE_SETTINGS
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsActions
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsSheet
import com.cooper.binge.companion.seerr.ui.tv.settings.groupPage
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_ADDRESS = "https://a-rather-long-hostname.living-room.home.example.org:5055/requests"
private const val LONG_SERVER = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr"
private const val OVERVIEW =
    "A long overview that runs on for several sentences, so that the page has a paragraph to wrap at a large font. " +
        "It keeps going past what any one screen shows at once, which is the case a clip is most likely to hide in."

private const val PHONE_PAGE_TEXTS = 2

/**
 * The television's pages with long text and every optional part present, at font scales 1.3 and 2.0, through the design
 * system's `assertNoTextClipped`: the request page, the issue sheet's pages, the settings board, the setup flow, the consent
 * question, the options hand-off and the error page. A page that scrolls is checked on its first screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPagesClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test fun `a request detail page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { RequestDetail() }

    @Test fun `a pending request page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { RequestDetail(pending = true) }

    @Test fun `a request's manage sheet at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { RequestDetail(manageOpen = true) }

    @Test fun `an issue sheet's thread at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { IssueSheet(ISSUE_PAGE_THREAD) }

    @Test fun `an issue sheet's card page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { IssueSheet(issueCardPage(KEY_REPORT)) }

    /** The page is its title, one line on what to do, and the code: two texts, both checked. */
    @Test fun `an issue sheet's phone page at 1_3`() =
        rule.assertNoTvClippingAt(FONT_SCALE_13, minTextNodes = PHONE_PAGE_TEXTS) { IssueSheet(ISSUE_PAGE_PHONE) }

    @Test fun `the settings sheet at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Settings() }

    @Test fun `the setup address page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { SetupAddress() }

    @Test fun `the setup sign-in page at 1_3`() =
        rule.assertNoTvClippingAt(FONT_SCALE_13) {
            SetupSignIn(SeerrSignInMode.Local, SetupError.Rejected)
        }

    @Test fun `the setup api key page at 1_3`() =
        rule.assertNoTvClippingAt(FONT_SCALE_13) {
            SetupSignIn(SeerrSignInMode.ApiKey, notice = SetupNotice.SessionRejected)
        }

    @Test fun `the consent question at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { TvConsentScreen(onChoice = {}) }

    @Test fun `the options hand-off at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Options(rejectedOptions) }

    @Test fun `the error page at 1_3`() =
        rule.assertNoTvClippingAt(
            FONT_SCALE_13,
        ) { TvSeerrErrorPage(SeerrError.Unreachable, onRetry = {}) }

    @Test fun `the quick connect plate at 1_3`() =
        rule.assertNoTvClippingAt(FONT_SCALE_13) {
            TvSetupLinkPlate(LinkFlow.QuickConnect(code = "472913"), onCancel = {})
        }

    @Test fun `the mark as sheet at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Sheet { MarkAs() } }

    @Test fun `the report sheet at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { RequestDetail(sheet = DetailSheet.Report) }

    @Test fun `the settings media server page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Settings(mediaPage = true) }

    /** The code page holds its title and one line, and the code, which is no text at all. */
    @Test
    fun `the bug report page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13, minTextNodes = 2) { Settings(showBugReport = true) }

    @Composable
    private fun Sheet(content: @Composable ColumnScope.() -> Unit) {
        Box(Modifier.fillMaxSize()) { TvSideSheetPanel(modifier = Modifier.align(Alignment.CenterEnd), content = content) }
    }

    @Composable
    private fun ColumnScope.MarkAs() =
        TvMarkAsSteps(
            media =
                MediaRecord(
                    mediaId = 900,
                    isTv = false,
                    instances =
                        listOf(
                            MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null),
                            MediaInstance(true, null, null, null, null),
                        ),
                    canSetStatus = true,
                    canClearData = false,
                    canDeleteFiles = false,
                ),
            onSelect = { _, _ -> },
            entryFocus = remember { FocusRequester() },
        )

    private fun detailActions() =
        TvRequestDetailActions(
            onBack = {},
            onRetry = {},
            onOpenInBinge = {},
            onApprove = {},
            onRetryRequest = {},
            onDecline = {},
            onRemove = {},
            onBlock = {},
            onSetMediaStatus = { _, _, _ -> },
        )

    @Composable
    private fun RequestDetail(
        pending: Boolean = false,
        manageOpen: Boolean = false,
        sheet: DetailSheet? = null,
    ) = TvRequestDetailScreen(
        state =
            RequestDetailUiState.Ready(
                requestDetail(
                    item = worstCaseRequest(),
                    actions = RequestActions(canApprove = pending, canDecline = pending, canRemove = true, canBlock = true),
                    canEdit = true,
                    overview = OVERVIEW,
                    canReportIssue = true,
                    canManageUsers = true,
                    destination =
                        RequestDestination(
                            serverName = "Sonarr 4K Remux Library on the Upstairs Server",
                            profileName = "Ultra-HD Remux with Lossless Audio and Subtitles",
                            rootFolder = "/mnt/storage/media/tv/4k-remux-collection",
                            tags = listOf("kids and family friendly", "4k-hdr", "remux"),
                        ),
                    media =
                        MediaRecord(
                            mediaId = 501,
                            isTv = true,
                            instances = listOf(MediaInstance(true, SeerrMediaStatusCode.PartiallyAvailable, null, null, null)),
                            canSetStatus = true,
                            canClearData = true,
                            canDeleteFiles = true,
                        ),
                ).copy(
                    seasons =
                        (1..6).map {
                            SeasonState(
                                it,
                                "Season $it: The Rings of Power, Extended Edition",
                                10,
                                SeerrMediaStatusCode.Processing,
                            )
                        },
                    downloads = listOf(DetailDownload(LONG_TITLE, 0.42f, 46_000_000_000_000L, 1_440)),
                ),
            ),
        events = emptyFlow(),
        actions = detailActions(),
        initialSheet = sheet,
        initialManageOpen = manageOpen,
    )

    @Composable
    private fun IssueSheet(page: String) {
        val detail = commonIssueDetail()
        TvIssueSheet(
            state =
                IssueDetailUiState.Ready(
                    detail =
                        detail.copy(
                            item = worstCaseIssue(),
                            report = detail.report?.copy(author = LONG_NAME, message = OVERVIEW),
                            comments = detail.comments.map { it.copy(author = LONG_NAME, message = OVERVIEW) },
                            canResolve = true,
                            canDelete = true,
                            mediaServerName = LONG_NAME,
                        ),
                ),
            actions = NoIssueSheetActions,
            initialPage = page,
        )
    }

    @Composable
    private fun Settings(
        showBugReport: Boolean = false,
        mediaPage: Boolean = false,
    ) = TvSettingsSheet(
        state =
            SettingsUiState.Ready(
                connection = ConnectionSummary(baseUrl = LONG_ADDRESS, signInKind = SignInKind.Session, userName = LONG_NAME),
                server =
                    ServerSummary(
                        title = LONG_SERVER,
                        variant = SeerrVariant.Jellyseerr,
                        versionLabel = "3.1.0-develop.2026.10.12",
                        updateAvailable = true,
                        commitsBehind = 1_204,
                    ),
                config =
                    ServerConfig(
                        services =
                            listOf(
                                ServerService(1, LONG_SERVER, ServiceType.Radarr, null, null, null, is4k = true, isDefault = true),
                                ServerService(2, LONG_SERVER, ServiceType.Sonarr, null, null, null, is4k = false, isDefault = true),
                            ),
                    ),
                app =
                    AppSettings(
                        bugReportUrl = "$LONG_ADDRESS/issues/new?template=bug.yml&app-version=0.1.0+%281%29",
                        shakeToReport = true,
                    ),
            ),
        actions = TvSettingsActions(),
        onDismissRequest = {},
        initialPage =
            when {
                showBugReport -> PAGE_BUG_REPORT
                mediaPage -> groupPage(stringResource(R.string.tv_settings_group_media))
                else -> PAGE_SETTINGS
            },
    )

    @Composable
    private fun SetupAddress() =
        TvSetupScreen(
            state =
                SetupUiState.Address(
                    serverUrl = LONG_ADDRESS,
                    insecure = true,
                    isInspecting = false,
                    error = SetupError.UnreachableNotLocal,
                    cleartextAllowed = false,
                    needsLocalNetwork = false,
                ),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    @Composable
    private fun SetupSignIn(
        mode: SeerrSignInMode,
        error: SetupError? = null,
        notice: SetupNotice? = null,
    ) = TvSetupScreen(
        state =
            SetupUiState.SignIn(
                server =
                    SetupServer(
                        baseUrl = LONG_ADDRESS,
                        title = LONG_SERVER,
                        variant = SeerrVariant.Jellyseerr,
                        versionLabel = "3.1.0-develop.2026.10.12",
                        mediaServerName = "Alexandria's Attic Jellyfin Server",
                        modes = listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local, SeerrSignInMode.ApiKey),
                        canResetPassword = true,
                        backdropUrl = null,
                    ),
                form =
                    SignInForm(
                        mode = mode,
                        email = "alexandria.fitzgerald-montgomery@a-rather-long-domain.example.com",
                        password = "hunter2",
                        apiKey = "MTc0NDE1NzQ0MjQyMzFhYzY0",
                    ),
                isConnecting = false,
                link = null,
                error = error,
                notice = notice,
            ),
        actions = NoSetupActions,
    )

    private val rejectedOptions = AdvancedRequestUiState.Ready(destination(), false, AdvancedRequestError.Rejected)

    private fun destination() =
        DestinationChoices(
            servers = listOf(Choice(1, "Radarr 4K Remux Library on the Upstairs Server"), Choice(2, "Radarr")),
            serverId = 1,
            profiles = listOf(Choice(10, "Ultra-HD Remux with Lossless Audio and Subtitles")),
            profileId = 10,
            rootFolders = listOf("/mnt/storage/media/movies/4k-remux-collection"),
            rootFolder = "/mnt/storage/media/movies/4k-remux-collection",
        )

    @Composable
    private fun Options(state: AdvancedRequestUiState) = TvAdvancedRequestScreen(state = state, actions = NoAdvancedActions)
}
