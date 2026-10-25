package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.plural
import com.cooper.binge.companion.seerr.util.requestItem
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.binge.designsystem.R as DesR

private const val MEDIA_ID = 42

/**
 * The request sheet's second step (#1054): declining asks only when it also blocks the title, removing always asks and
 * says when a download or files stay (#1721), and deleting files asks once, naming the client, with the title's open requests offered alongside (#1720).
 */
@RunWith(RobolectricTestRunner::class)
class RequestActionsSheetConfirmTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private val declined = mutableListOf<Boolean>()
    private val removed = mutableListOf<Boolean>()
    private val deleted = mutableListOf<Triple<Int, Boolean, List<Int>>>()
    private var dismissed = 0

    @Test
    fun `declining without blocking the title declines at once`() {
        setSheet()

        click(R.string.request_decline)

        assertEquals(listOf(false), declined)
        assertEquals(1, dismissed)
        composeTestRule.onNodeWithText(string(R.string.request_decline_block_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun `declining and blocking the title asks first, then declines with the block`() {
        setSheet()

        click(R.string.request_block_title)
        click(R.string.request_decline_and_block)

        composeTestRule.onNodeWithText(string(R.string.request_decline_block_confirm_title)).assertExists()
        assertEquals(emptyList<Boolean>(), declined)
        confirm(R.string.request_decline)

        assertEquals(listOf(true), declined)
        assertEquals(1, dismissed)
    }

    @Test
    fun `removing always asks, and backing out of it removes nothing`() {
        setSheet()

        click(R.string.request_remove)
        composeTestRule.onNodeWithText(string(R.string.request_remove_confirm_title)).assertExists()
        composeTestRule.onNodeWithText(string(DesR.string.action_cancel)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(emptyList<Boolean>(), removed)
        composeTestRule.onNodeWithText(string(R.string.request_remove_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun `removing a request with nothing downloading or downloaded keeps the copy as it was`() {
        setSheet()

        click(R.string.request_remove)

        composeTestRule.onNodeWithText(string(R.string.request_remove_confirm_message)).assertExists()
    }

    /** Seerr's request delete never calls Radarr or Sonarr, so a running download carries on (#1721). */
    @Test
    fun `removing a request mid-download says the download stays, and points at Delete files beside it`() {
        setSheet(item = heat(download = RequestDownload(fraction = 0.4f, etaMinutes = 10, downloading = true)))

        click(R.string.request_remove)

        val note = string(R.string.request_remove_files_stay_delete)
        composeTestRule.onNodeWithText("${string(R.string.request_remove_confirm_message)} $note").assertExists()
    }

    @Test
    fun `removing a request whose files are on disk, where the sheet has no Delete files, only says they stay`() {
        setSheet(item = heat(mediaStatus = SeerrMediaStatusCode.Available), canDeleteFiles = false)

        click(R.string.request_remove)

        val note = string(R.string.request_remove_files_stay)
        composeTestRule.onNodeWithText("${string(R.string.request_remove_confirm_message)} $note").assertExists()
    }

    @Test
    fun `removing and blocking a downloaded title says the files stay too`() {
        setSheet(item = heat(mediaStatus = SeerrMediaStatusCode.PartiallyAvailable))

        click(R.string.request_block_title)
        click(R.string.request_remove_and_block)

        val note = string(R.string.request_remove_files_stay_delete)
        composeTestRule.onNodeWithText("${string(R.string.request_remove_block_confirm_message)} $note").assertExists()
    }

    @Test
    fun `deleting a movie's files asks once, in Radarr's words, and the sheet stays to show the result`() {
        setSheet()

        click(R.string.media_delete_files)
        val radarr = string(R.string.media_client_radarr)
        composeTestRule.onNodeWithText(string(R.string.media_delete_files_confirm_title, radarr)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.media_delete_files_confirm_movie, radarr)).assertExists()
        confirm(R.string.media_delete_files)

        assertEquals(listOf(Triple(MEDIA_ID, false, emptyList<Int>())), deleted)
        assertEquals(0, dismissed)
        composeTestRule.onNodeWithText(string(R.string.media_delete_files_confirm_title, radarr)).assertDoesNotExist()
    }

    @Test
    fun `a show's confirm names Sonarr and says every season goes`() {
        setSheet(isTv = true)

        click(R.string.media_delete_files)

        val sonarr = string(R.string.media_client_sonarr)
        composeTestRule.onNodeWithText(string(R.string.media_delete_files_confirm_title, sonarr)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.media_delete_files_confirm_tv, sonarr)).assertExists()
    }

    @Test
    fun `with no open request on the instance the confirm offers nothing to remove`() {
        setSheet(requests = listOf(summary(id = 11, status = SeerrRequestStatusCode.Declined), summary(id = 12, is4k = true)))

        click(R.string.media_delete_files)

        composeTestRule.onNodeWithText(plural(R.plurals.media_delete_files_remove_requests, 1)).assertDoesNotExist()
    }

    @Test
    fun `ticking the requests box deletes the files and removes the instance's open requests`() {
        setSheet(
            requests =
                listOf(
                    summary(id = 11),
                    summary(id = 12, status = SeerrRequestStatusCode.Approved),
                    summary(id = 13, status = SeerrRequestStatusCode.Declined),
                    summary(id = 14, is4k = true),
                ),
        )

        click(R.string.media_delete_files)
        composeTestRule.onNodeWithText(plural(R.plurals.media_delete_files_remove_requests, 2)).performClick()
        composeTestRule.waitForIdle()
        confirm(R.string.media_delete_files_and_requests)

        assertEquals(listOf(Triple(MEDIA_ID, false, listOf(11, 12))), deleted)
    }

    @Test
    fun `an instance whose files went says so in place of its delete row`() {
        setSheet(filesDeletion = FilesDeletion.Deleted)

        composeTestRule.onNodeWithText(string(R.string.media_files_deleted)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.media_delete_files)).assertDoesNotExist()
    }

    /** Seerr's 409 for want of a default server is a setting to change, so the row says which and offers no delete (#1726). */
    @Test
    fun `an instance Seerr has no default server for says what to set, in place of its delete row`() {
        setSheet(isTv = true, filesDeletion = FilesDeletion.NoDefaultServer)

        composeTestRule.onNodeWithText(string(R.string.media_files_not_deleted)).assertExists()
        val sonarr = string(R.string.media_client_sonarr)
        composeTestRule.onNodeWithText(string(R.string.media_files_no_default_server, sonarr)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.media_delete_files)).assertDoesNotExist()
        assertTrue("no delete is offered again", deleted.isEmpty())
    }

    private fun heat(
        mediaStatus: SeerrMediaStatusCode? = null,
        download: RequestDownload? = null,
    ) = requestItem(
        id = 11,
        tmdbId = 949,
        title = "Heat",
        status = SeerrRequestStatusCode.Pending,
        mediaStatus = mediaStatus,
        download = download,
    )

    private fun setSheet(
        isTv: Boolean = false,
        requests: List<RequestSummary> = emptyList(),
        filesDeletion: FilesDeletion? = null,
        item: RequestItem = heat(),
        canDeleteFiles: Boolean = true,
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestActionsSheet(
                    item = item,
                    actions = RequestActions(canApprove = true, canDecline = true, canRemove = true, canBlock = true),
                    onApprove = {},
                    onRetry = {},
                    onDecline = { declined += it },
                    onRemove = { removed += it },
                    onDismiss = { dismissed++ },
                    media =
                        MediaRecord(
                            mediaId = MEDIA_ID,
                            isTv = isTv,
                            instances =
                                listOf(
                                    MediaInstance(
                                        is4k = false,
                                        status = SeerrMediaStatusCode.Available,
                                        serviceUrl = null,
                                        mediaServerUrl = null,
                                        watch = null,
                                        filesDeletion = filesDeletion,
                                    ),
                                ),
                            canSetStatus = false,
                            canClearData = false,
                            canDeleteFiles = canDeleteFiles,
                        ),
                    mediaActions =
                        ManageMediaActions(
                            onSetStatus = { _, _, _ -> },
                            onClearData = {},
                            onDeleteFiles = { id, is4k, requestIds -> deleted += Triple(id, is4k, requestIds) },
                        ),
                    requests = requests,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** By the row's own action rather than a tap at its centre: a row low in the sheet can sit below the window. */
    private fun click(label: Int) {
        composeTestRule.onNodeWithText(string(label)).performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
    }

    private fun summary(
        id: Int,
        status: SeerrRequestStatusCode = SeerrRequestStatusCode.Pending,
        is4k: Boolean = false,
    ) = RequestSummary(id = id, status = status, requestedBy = null, requestedAtMillis = null, is4k = is4k, seasonNumbers = emptyList())

    /** The dialog's confirm shares its label with the row that opened it; the dialog's is the last drawn. */
    private fun confirm(label: Int) {
        composeTestRule.onAllNodesWithText(string(label)).onLast().performClick()
        composeTestRule.waitForIdle()
    }
}
