package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.binge.designsystem.tv.component.TvSideSheetPanel
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
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDestination
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.tv.NoAdvancedActions
import com.cooper.binge.companion.seerr.ui.tv.NoSetupActions
import com.cooper.binge.companion.seerr.ui.tv.TvAdvancedRequestScreen
import com.cooper.binge.companion.seerr.ui.tv.TvConsentScreen
import com.cooper.binge.companion.seerr.ui.tv.TvSeerrErrorPage
import com.cooper.binge.companion.seerr.ui.tv.TvSetupLinkPlate
import com.cooper.binge.companion.seerr.ui.tv.TvSetupScreen
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueDetailActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.requests.TvMarkAsSteps
import com.cooper.binge.companion.seerr.ui.tv.requests.TvReportDraft
import com.cooper.binge.companion.seerr.ui.tv.requests.TvReportIssueStep
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsBoard
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

/**
 * The television's pages with long text and every optional part present, at font scales 1.3 and 2.0, through the design
 * system's `assertNoTextClipped`: the request and issue detail pages, the settings board, the setup flow, the consent
 * question, the options hand-off and the error page. A page that scrolls is checked on its first screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TvPagesClippingTest {
    @get:Rule
    val rule = createSeerrKeyboardComposeRule()

    @Test fun `a request detail page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { RequestDetail() }

    @Test fun `a pending request page offering seven actions at 1_3`() =
        rule.assertNoTvClippingAt(FONT_SCALE_13) {
            RequestDetail(sevenActions = true)
        }

    @Test fun `an issue detail page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { IssueDetail() }

    @Test fun `the settings board at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Settings() }

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

    @Test fun `the report sheet at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Sheet { Report() } }

    @Test fun `the bug report page at 1_3`() = rule.assertNoTvClippingAt(FONT_SCALE_13) { Settings(showBugReport = true) }

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

    @Composable
    private fun ColumnScope.Report() =
        TvReportIssueStep(
            report = IssueReport.Idle,
            draft = TvReportDraft(type = IssueType.Video, message = OVERVIEW, onTypeChange = {}, onMessageChange = {}),
            actions = detailActions(),
            onDone = {},
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
            onReportIssue = { _, _ -> },
            onDismissReport = {},
        )

    @Composable
    private fun RequestDetail(sevenActions: Boolean = false) =
        TvRequestDetailScreen(
            state =
                RequestDetailUiState.Ready(
                    requestDetail(
                        item = worstCaseRequest(),
                        actions = RequestActions(canApprove = sevenActions, canDecline = true, canRemove = true, canBlock = true),
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
        )

    @Composable
    private fun IssueDetail() {
        val detail = commonIssueDetail()
        TvIssueDetailScreen(
            state =
                IssueDetailUiState.Ready(
                    detail =
                        detail.copy(
                            item = worstCaseIssue(),
                            report = detail.report?.copy(author = LONG_NAME, message = OVERVIEW),
                            comments = detail.comments.map { it.copy(author = LONG_NAME, message = OVERVIEW) },
                        ),
                ),
            actions = TvIssueDetailActions(onBack = {}, onRetry = {}),
        )
    }

    @Composable
    private fun Settings(showBugReport: Boolean = false) =
        TvSettingsBoard(
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
                    config = ServerConfig(),
                    app =
                        AppSettings(
                            bugReportUrl = "$LONG_ADDRESS/issues/new?template=bug.yml&app-version=0.1.0+%281%29",
                            shakeToReport = true,
                        ),
                ),
            onEditConnection = {},
            onDisconnect = {},
            initialShowingBugReport = showBugReport,
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
