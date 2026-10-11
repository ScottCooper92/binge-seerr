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
 * and deleting files asks twice, the second time naming the client the files leave.
 */
@RunWith(RobolectricTestRunner::class)
class RequestActionsSheetConfirmTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private val declined = mutableListOf<Boolean>()
    private val removed = mutableListOf<Boolean>()
    private val deleted = mutableListOf<Pair<Int, Boolean>>()
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
    fun `deleting files asks twice, the second time naming Radarr, then deletes`() {
        setSheet()

        click(R.string.media_delete_files)
        composeTestRule.onNodeWithText(string(R.string.media_delete_files_confirm_title)).assertExists()
        confirm(R.string.media_delete_files)

        val radarr = string(R.string.media_client_radarr)
        composeTestRule.onNodeWithText(string(R.string.media_delete_files_second_title, radarr)).assertExists()
        assertEquals(emptyList<Pair<Int, Boolean>>(), deleted)
        confirm(R.string.media_delete_files)

        assertEquals(listOf(MEDIA_ID to false), deleted)
        assertEquals(1, dismissed)
    }

    private fun setSheet() {
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
                            isTv = false,
                            instances =
                                listOf(
                                    MediaInstance(
                                        is4k = false,
                                        status = SeerrMediaStatusCode.Available,
                                        serviceUrl = null,
                                        mediaServerUrl = null,
                                        watch = null,
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
                            onDeleteFiles = { id, is4k -> deleted += id to is4k },
                        ),
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

    /** The dialog's confirm shares its label with the row that opened it; the dialog's is the last drawn. */
    private fun confirm(label: Int) {
        composeTestRule.onAllNodesWithText(string(label)).onLast().performClick()
        composeTestRule.waitForIdle()
    }
}
