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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.binge.designsystem.R as DesR

private const val MEDIA_ID = 42

/**
 * The request sheet's second step (#1054): declining asks only when it also blocks the title, removing always asks,
 * and deleting files asks once, naming the client, with the title's open requests offered alongside (#1720).
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
        setSheet(filesDeleted = true)

        composeTestRule.onNodeWithText(string(R.string.media_files_deleted)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.media_delete_files)).assertDoesNotExist()
    }

    private fun setSheet(
        isTv: Boolean = false,
        requests: List<RequestSummary> = emptyList(),
        filesDeleted: Boolean = false,
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestActionsSheet(
                    item = requestItem(id = 11, tmdbId = 949, title = "Heat", status = SeerrRequestStatusCode.Pending),
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
                                        filesDeleted = filesDeleted,
                                    ),
                                ),
                            canSetStatus = false,
                            canClearData = false,
                            canDeleteFiles = true,
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
