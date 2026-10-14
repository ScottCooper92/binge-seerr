package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestActions
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.requestDetail
import com.cooper.binge.companion.seerr.util.requestItem
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

private const val HEAT = "Heat"

/**
 * The board's row through to its read-only detail page and back, under a real Back key rather than a
 * directional dismiss: OK on the row reaches the page with the D-pad, and Back returns focus to the row
 * that opened it. The page's own primary action is covered here too, since it is what the row's moderation
 * moved onto: Approve and Decline run at once from the action row, and Remove and Block, in Manage, ask first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestDetailFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val approved = mutableListOf<Int>()
    private val declined = mutableListOf<Boolean>()
    private val removed = mutableListOf<Boolean>()
    private var blocked = 0

    /** What the page offers, which a successful moderation changes as the real page's reload does. */
    private var offered by mutableStateOf(RequestActions())

    @Test
    fun okOnTheRowReachesThePageOnItsFirstActionAndBackReturnsFocusToTheRow() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        pressOk()
        actionButton(R.string.tv_detail_approve).assertIsFocused()

        pressBack()
        settleFocusRestore()

        row(HEAT).assertIsFocused()
    }

    @Test
    fun approveRunsAtOnceAndKeepsFocusOnTheButton() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_approve).assertIsFocused()

        pressOk()

        assertEquals(listOf(1), approved)
        actionButton(R.string.tv_detail_approve).assertIsFocused()
    }

    @Test
    fun declineIsAStepRightOfApproveAndKeepsTheRequest() {
        setContent()
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()

        pressRight()
        actionButton(R.string.tv_detail_decline).assertIsFocused()
        pressOk()

        assertEquals(listOf(false), declined)
    }

    /** Remove is in Manage; it asks first, and Cancel hands focus back to Manage, where the viewer came from. */
    @Test
    fun removeAsksFirstAndCancelReturnsToManage() {
        setContent(RequestActions(canRemove = true))
        openPage()
        openManageRow(R.string.tv_detail_remove)

        pressOk()
        composeTestRule.onNodeWithText(string(R.string.request_remove_confirm_title)).assertIsDisplayed()
        assertEquals(emptyList<Boolean>(), removed)
        pressCancel()
        settleFocusRestore()

        assertEquals(emptyList<Boolean>(), removed)
        actionButton(R.string.tv_detail_manage).assertIsFocused()
    }

    @Test
    fun blockingTheTitleAsksFirstThenBlocksOnConfirm() {
        setContent(RequestActions(canBlock = true))
        openPage()
        openManageRow(R.string.tv_detail_block)

        pressOk()
        composeTestRule.onNodeWithText(string(R.string.request_block_confirm_title)).assertIsDisplayed()
        assertEquals(0, blocked)
        confirmBlock()

        assertEquals(1, blocked)
    }

    /** A successful approve reloads the page without Approve; focus stays on the action row, not the root (#801). */
    @Test
    fun approvingRemovesApproveAndFocusStaysOnTheActionRow() {
        setContent(afterApprove = RequestActions(canDecline = true))
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(R.string.tv_detail_approve).assertIsFocused()

        pressOk()
        composeTestRule.waitForIdle()

        actionButton(R.string.tv_detail_approve).assertDoesNotExist()
        actionButton(R.string.tv_detail_decline).assertIsFocused()
    }

    /** A successful decline reloads the page without Approve or Decline; focus moves to what is left on the row (#801). */
    @Test
    fun decliningRemovesDeclineAndFocusStaysOnTheActionRow() {
        setContent(
            allowed = RequestActions(canApprove = true, canDecline = true, canRemove = true),
            afterDecline = RequestActions(canRemove = true),
        )
        openPage()
        pressRight()
        actionButton(R.string.tv_detail_decline).assertIsFocused()

        pressOk()
        composeTestRule.waitForIdle()

        assertEquals(listOf(false), declined)
        actionButton(R.string.tv_detail_decline).assertDoesNotExist()
        actionButton(R.string.tv_detail_manage).assertIsFocused()
    }

    /**
     * A successful block reloads the page without Block, after the confirm has handed focus back to Manage, as the real
     * reload lands once the server answers; focus stays on the row, on Manage, which still holds Remove (#801).
     */
    @Test
    fun blockingRemovesBlockAndFocusStaysOnTheActionRow() {
        setContent(RequestActions(canBlock = true, canRemove = true))
        openPage()
        openManageRow(R.string.tv_detail_block)
        pressOk()
        confirmBlock()
        settleFocusRestore()
        assertEquals(1, blocked)
        actionButton(R.string.tv_detail_manage).assertIsFocused()

        offered = RequestActions(canRemove = true)
        composeTestRule.waitForIdle()

        actionButton(R.string.tv_detail_manage).assertIsFocused()
    }

    /** A block that leaves no action behind hands focus to the synopsis rather than the root (#801). */
    @Test
    fun blockingTheLastActionLeavesFocusOnTheSynopsis() {
        setContent(RequestActions(canBlock = true))
        openPage()
        openManageRow(R.string.tv_detail_block)
        pressOk()
        confirmBlock()
        settleFocusRestore()
        assertEquals(1, blocked)

        offered = RequestActions()
        composeTestRule.waitForIdle()

        actionButton(R.string.tv_detail_manage).assertDoesNotExist()
        composeTestRule.onNode(isFocused()).assertExists()
    }

    /**
     * The read-only case the board's row doesn't reach: an already-available title with no active downloads,
     * no moderation this viewer can do, and no Open in Binge to hand off to. Arrival has nothing in reading
     * order to offer focus to, so it must fall back to the content column itself rather than leaving the
     * D-pad dead.
     */
    @Test
    fun aFailedLoadLandsOnRetry() {
        var retries = 0
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestDetailScreen(
                    state = RequestDetailUiState.Error(SeerrError.Server),
                    events = emptyFlow(),
                    actions =
                        TvRequestDetailActions(
                            onBack = {},
                            onRetry = { retries++ },
                            onOpenInBinge = null,
                            onApprove = {},
                            onRetryRequest = {},
                            onDecline = {},
                            onRemove = {},
                            onBlock = {},
                            onSetMediaStatus = { _, _, _ -> },
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(string(DesR.string.error_kind_retry)) and isFocusable()).assertIsFocused()
        pressOk()
        assertEquals(1, retries)
    }

    @Test
    fun arrivalFallsBackToTheContentColumnWhenNothingElseIsFocusable() {
        val detail = requestDetail(item = requestItem(id = 2), viewerId = null)
        composeTestRule.setContent {
            BingeTvTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    TvRequestDetailScreen(
                        state = RequestDetailUiState.Ready(detail),
                        events = emptyFlow(),
                        actions =
                            TvRequestDetailActions(
                                onBack = {},
                                onRetry = {},
                                onOpenInBinge = null,
                                onApprove = {},
                                onRetryRequest = {},
                                onDecline = {},
                                onRemove = {},
                                onBlock = {},
                                onSetMediaStatus = { _, _, _ -> },
                            ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(isFocused()).assertExists()
    }

    private fun setContent(
        allowed: RequestActions = RequestActions(canApprove = true, canDecline = true),
        afterApprove: RequestActions? = null,
        afterDecline: RequestActions? = null,
    ) {
        offered = allowed
        val item = requestItem(status = SeerrRequestStatusCode.Pending, title = HEAT)
        val detail = requestDetail(item = item, canEdit = true, canManageUsers = true)
        composeTestRule.setContent {
            var openId by remember { mutableStateOf<Int?>(null) }
            val scope =
                ModerationScope(permissions = SeerrPermissions(canManageRequests = true), currentUserId = 7, hasBlocklist = false)
            BingeTvTheme {
                // The page is the shell's overlay, as the app hosts it, so its close goes through the shell's hand-off.
                BoardInShell(
                    destination = TvDestination.Hub,
                    overlay =
                        openId?.let {
                            {
                                TvRequestDetailScreen(
                                    state = RequestDetailUiState.Ready(detail.copy(actions = offered)),
                                    events = emptyFlow(),
                                    actions =
                                        TvRequestDetailActions(
                                            onBack = { openId = null },
                                            onRetry = {},
                                            onOpenInBinge = null,
                                            onApprove = {
                                                approved += item.id
                                                afterApprove?.let { offered = it }
                                            },
                                            onRetryRequest = {},
                                            onDecline = {
                                                declined += it
                                                afterDecline?.let { next -> offered = next }
                                            },
                                            onRemove = { removed += it },
                                            onBlock = { blocked++ },
                                            onSetMediaStatus = { _, _, _ -> },
                                        ),
                                )
                            }
                        },
                ) {
                    TvRequestsRowsBoard(
                        state =
                            RequestsUiState.Ready(
                                filter = RequestFilter.All,
                                sort = RequestSort.Added,
                                counts = null,
                                scope = scope,
                                listVersion = 0,
                            ),
                        rowsFor = { filter ->
                            if (filter ==
                                RequestFilter.Pending
                            ) {
                                TvPagedRows(count = 1, at = { item })
                            } else {
                                TvPagedRows(count = 0, at = { null })
                            }
                        },
                        actions =
                            TvRequestsActions(
                                onOpenDetail = { openId = it.id },
                                onSeeAll = {},
                                onRetryLoad = {},
                                onRetryScope = {},
                            ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun row(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    /** A resting icon button names itself by description and a labelled one by text; either way the surface is the focusable node. */
    private fun actionButton(label: Int) =
        composeTestRule.onNode(
            isFocusable() and
                (hasContentDescription(string(label)) or hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun pressOk() = press(Key.DirectionCenter)

    /** OK on the board's row, which reaches the page on its first action. */
    private fun openPage() {
        row(HEAT).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
    }

    /** Manage, then [label]'s row in its sheet, focused. */
    private fun openManageRow(label: Int) {
        actionButton(R.string.tv_detail_manage).requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
        actionButton(label).requestFocus()
        composeTestRule.waitForIdle()
        actionButton(label).assertIsFocused()
    }

    /** Block in the confirm dialog. */
    private fun confirmBlock() {
        composeTestRule.onAllNodes(hasText(string(R.string.tv_detail_block)) and isFocusable()).onLast().requestFocus()
        composeTestRule.waitForIdle()
        pressOk()
    }

    private fun pressRight() = press(Key.DirectionRight)

    /** Cancel in the confirm dialog, which is a window of its own: the dialog hands focus back to the button beneath it. */
    private fun pressCancel() {
        composeTestRule.onNodeWithText(string(DesR.string.action_cancel)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** The shell hands focus back into the board once the page above the rail has closed. */
    private fun settleFocusRestore() = composeTestRule.settleShellHandOff()

    private fun press(key: Key) {
        // A confirm dialog is a second root, drawn over the page; keys go to the topmost.
        composeTestRule.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
