package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.handoff.AddressCandidate
import com.cooper.binge.companion.seerr.handoff.AddressLocality
import com.cooper.binge.companion.seerr.handoff.AddressSource
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import com.cooper.binge.companion.seerr.ui.SignInForm
import com.cooper.binge.companion.seerr.ui.handoff.SendAddressActions
import com.cooper.binge.companion.seerr.ui.handoff.SendAddressSheetContent
import com.cooper.binge.companion.seerr.ui.handoff.SendAddressUiState
import com.cooper.binge.companion.seerr.ui.handoff.SignInOffer
import com.cooper.binge.companion.seerr.ui.handoff.SignInStep
import com.cooper.binge.companion.seerr.ui.issues.CommentActionsContent
import com.cooper.binge.companion.seerr.ui.issues.IssueManageContent
import com.cooper.binge.companion.seerr.ui.issues.OutboxActionsContent
import com.cooper.binge.companion.seerr.ui.requests.EditRequestActions
import com.cooper.binge.companion.seerr.ui.requests.EditRequestContent
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusSheetContent
import com.cooper.binge.companion.seerr.ui.requests.ReportIssueContent
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestActionsContent
import com.cooper.binge.companion.seerr.ui.requests.RequestOpenSheetContent
import com.cooper.binge.companion.seerr.ui.requests.RequestSheetCallbacks
import com.cooper.binge.companion.seerr.ui.requests.RequestSheetModel
import com.cooper.binge.companion.seerr.ui.requests.SeasonChoice
import com.cooper.binge.companion.seerr.ui.requests.rememberRequestOpenLinks
import com.cooper.binge.companion.seerr.ui.users.AdmissionChoiceContent
import com.cooper.binge.companion.seerr.ui.users.CreateUserDraft
import com.cooper.binge.companion.seerr.ui.users.CreateUserSheetContent
import com.cooper.binge.companion.seerr.ui.users.ImportCandidate
import com.cooper.binge.companion.seerr.ui.users.ImportPicker
import com.cooper.binge.companion.seerr.ui.users.ImportUsersContent
import com.cooper.binge.companion.seerr.ui.users.PermissionsEditorContent
import com.cooper.binge.companion.seerr.ui.users.UserAdmissionActions
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_EMAIL = "alexandria.fitzgerald-montgomery@a-rather-long-domain-name.example.com"
private const val LONG_SERVICE = "Radarr 4K Remux Library on the Upstairs Server"

/**
 * The bottom sheets' bodies with long names, addresses and every optional row, at font scales 1.3 and 2.0, through the
 * design system's `assertNoTextClipped`. A sheet's body is rendered inline, as the screenshot frames do, because a modal
 * window is not in the test's tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the user sheets at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { UserSheets() }

    @Test fun `the user sheets at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { UserSheets() }

    @Test fun `the request sheets at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { RequestSheets() }

    @Test fun `the request sheets at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { RequestSheets() }

    @Test fun `the issue sheets at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { IssueSheets() }

    @Test fun `the issue sheets at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { IssueSheets() }

    @Test fun `the send address sheet at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { SendAddressSheets() }

    @Test fun `the send address sheet at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { SendAddressSheets() }

    @Composable
    private fun UserSheets() {
        Column {
            CreateUserSheetContent(
                draft = CreateUserDraft(email = LONG_EMAIL, username = LONG_NAME, password = "hunter2hunter2", canGeneratePassword = true),
                saving = false,
                onEditDraft = {},
                onCreate = {},
            )
            AdmissionChoiceContent(importSource = UserOrigin.Plex, actions = admissionActions())
            ImportUsersContent(
                picker =
                    ImportPicker(
                        source = UserOrigin.Plex,
                        candidates =
                            listOf(
                                ImportCandidate("1", LONG_NAME, LONG_EMAIL, null),
                                ImportCandidate("2", "Grace Hopper", null, null),
                            ),
                        selected = setOf("2"),
                    ),
                saving = false,
                actions = admissionActions(),
            )
            PermissionsEditorContent(
                offered = ManageablePermission.entries.toList(),
                selected = setOf(ManageablePermission.ManageRequests),
                title = "Permissions for $LONG_NAME",
                saving = false,
                onToggle = {},
                onSave = {},
                locked = setOf(ManageablePermission.ManageUsers),
            )
        }
    }

    private fun admissionActions() =
        UserAdmissionActions(
            onStart = {},
            onCancel = {},
            onStartCreate = {},
            onEditDraft = {},
            onCreate = {},
            onStartImport = {},
            onToggleCandidate = {},
            onSelectAllCandidates = {},
            onImport = {},
        )

    @Composable
    private fun RequestSheets() {
        val detail =
            requestDetail(
                item = worstCaseRequest(),
                actions = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true),
                canEdit = true,
            )
        Column {
            RequestActionsContent(
                model = RequestSheetModel(item = detail.item, actions = detail.actions, canEdit = detail.canEdit, media = detail.media),
                callbacks = RequestSheetCallbacks(onApprove = {}, onRetry = {}, onDecline = {}, onRemove = {}),
                blockTitle = false,
                onBlockTitleChange = {},
            )
            MediaStatusSheetContent(
                instance =
                    MediaInstance(
                        is4k = true,
                        status = SeerrMediaStatusCode.PartiallyAvailable,
                        serviceUrl = null,
                        mediaServerUrl = null,
                        watch = null,
                    ),
                onSelect = {},
            )
            ReportIssueContent(report = IssueReport.Idle, onSend = { _, _ -> })
            RequestOpenSheetContent(links = rememberRequestOpenLinks(detail), onOpen = {})
            EditRequestContent(
                item = detail.item,
                edit =
                    EditState(
                        seasons =
                            (1..6).map {
                                SeasonChoice(
                                    it,
                                    "Season $it: The Rings of Power, Extended Edition",
                                    10,
                                    selected = it > 3,
                                    heldStatus = null,
                                )
                            },
                        destination =
                            DestinationChoices(
                                servers = listOf(Choice(1, LONG_SERVICE)),
                                serverId = 1,
                                profiles = listOf(Choice(4, "Ultra-HD Remux with Lossless Audio and Subtitles")),
                                profileId = 4,
                                rootFolders = listOf("/mnt/storage/media/tv/4k-remux-collection"),
                                rootFolder = "/mnt/storage/media/tv/4k-remux-collection",
                            ),
                    ),
                actions =
                    EditRequestActions(
                        onToggleSeason = {},
                        onSelectAllSeasons = {},
                        onSelectServer = {},
                        onSelectProfile = {},
                        onSelectRootFolder = {},
                        onToggleTag = {},
                        onSave = {},
                        onDismiss = {},
                    ),
            )
        }
    }

    @Composable
    private fun IssueSheets() {
        Column {
            IssueManageContent("Alexandria's Front Room Jellyseerr", "Plex Media Server Upstairs", LONG_SERVICE, onOpenWeb = {
            }, onOpenMediaServer = {}, onOpenService = {})
            CommentActionsContent(onEdit = {}, onDelete = {})
            OutboxActionsContent(retryable = true, onRetry = {}, onEdit = {}, onDrop = {})
        }
    }

    @Composable
    private fun SendAddressSheets() {
        val candidates =
            listOf(
                AddressCandidate("http://192.168.86.20:5055/with/a/rather/long/path", AddressSource.Remembered, AddressLocality.Local),
                AddressCandidate(
                    "https://a-rather-long-hostname.living-room.example.com/",
                    AddressSource.ApplicationUrl,
                    AddressLocality.Unknown,
                ),
                AddressCandidate("http://100.103.24.117:30042/", AddressSource.Connected, AddressLocality.NotLocal),
            )
        Column {
            SendAddressSheetContent(
                state =
                    SendAddressUiState.Ready(
                        tv = "192.168.86.53",
                        candidates = candidates,
                        address = candidates.first().address,
                        isSending = false,
                        failed = true,
                        signIn = SignInOffer(userName = LONG_NAME),
                        signInChosen = true,
                    ),
                actions = SendAddressActions(),
            )
            SendAddressSheetContent(
                state =
                    SendAddressUiState.SigningIn(
                        "192.168.86.53",
                        SignInStep.Form(
                            "Alexandria's Front Room Jellyseerr",
                            listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local, SeerrSignInMode.Plex),
                            SignInForm(mode = SeerrSignInMode.Local, email = LONG_EMAIL, password = "secret"),
                            address = "http://192.168.1.66:5055/with/a/rather/long/path",
                            rejected = true,
                        ),
                    ),
                actions = SendAddressActions(),
            )
        }
    }
}
