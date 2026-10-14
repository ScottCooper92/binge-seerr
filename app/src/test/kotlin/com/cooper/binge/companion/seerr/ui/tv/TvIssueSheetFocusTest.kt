package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueSheet
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueSheetActions
import com.cooper.binge.companion.seerr.ui.tv.issues.sampleIssueSheetState
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val REPORT = "The English subtitles drift"
private const val GRACE = "Thanks, I can see it too"
private const val MINE = "Swapped the subtitles"
private const val GRACE_ID = 101
private const val MINE_ID = 102

/**
 * The issue sheet under a real D-pad. Focus arrives on the report. Left steps back along the action buttons and closes the
 * sheet only from the first; anywhere else it closes the sheet. A page a card or row opens hands focus back to it on Back.
 * A deleted comment hands focus to the card after it, else the one before, else the reply row. Deletes ask first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvIssueSheetFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf<IssueDetailUiState>(sampleIssueSheetState())
    private var dismissals = 0
    private var toggles = 0
    private var issueDeletes = 0
    private var opened = 0
    private val commentDeletes = mutableListOf<Int>()

    @Test
    fun focusArrivesOnTheReport() {
        setSheet()

        card(REPORT).assertIsFocused()
    }

    @Test
    fun leftStepsBackAlongTheButtonsAndClosesOnlyFromTheFirst() {
        setSheet()
        button(R.string.issue_delete).requestFocus()
        composeTestRule.waitForIdle()

        composeTestRule.press(Key.DirectionLeft)
        button(openInJellyfin()).assertIsFocused()
        composeTestRule.press(Key.DirectionLeft)
        button(R.string.issue_action_resolve).assertIsFocused()
        assertEquals(0, dismissals)

        composeTestRule.press(Key.DirectionLeft)

        assertEquals(1, dismissals)
    }

    @Test
    fun leftOnACardClosesTheSheet() {
        setSheet()
        card(REPORT).assertIsFocused()

        composeTestRule.press(Key.DirectionLeft)

        assertEquals(1, dismissals)
    }

    @Test
    fun theReportOpensItsPageAndBackReturnsToIt() {
        setSheet()

        composeTestRule.press(Key.DirectionCenter)
        row(R.string.tv_issue_edit_on_phone).assertExists()
        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        card(REPORT).assertIsFocused()
    }

    @Test
    fun replyOnYourPhoneShowsTheCodeAndBackReturnsToTheRow() {
        setSheet()
        row(R.string.tv_issue_reply_on_phone).requestFocus()

        composeTestRule.press(Key.DirectionCenter)
        composeTestRule.onNode(hasText(string(R.string.tv_issue_phone_body, "Seerr"))).assertExists()
        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        row(R.string.tv_issue_reply_on_phone).assertIsFocused()
    }

    /** Two pages deep and back: each Back lands on the row or card that opened the page it leaves. */
    @Test
    fun editingACommentOnThePhoneComesBackToTheRowThenTheCard() {
        setSheet()
        card(MINE).requestFocus()
        composeTestRule.press(Key.DirectionCenter)
        row(R.string.tv_issue_edit_on_phone).requestFocus()
        composeTestRule.press(Key.DirectionCenter)

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()
        row(R.string.tv_issue_edit_on_phone).assertIsFocused()
        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        card(MINE).assertIsFocused()
    }

    @Test
    fun aDeletedCommentHandsFocusToTheCardAfterIt() {
        assertDeletingLandsOn(GRACE_ID, card = GRACE, landsOn = { card(MINE) })
    }

    @Test
    fun theLastCommentDeletedHandsFocusToTheCardBeforeIt() {
        assertDeletingLandsOn(MINE_ID, card = MINE, landsOn = { card(GRACE) })
    }

    @Test
    fun theOnlyCommentDeletedHandsFocusToTheReplyRow() {
        val ready = sampleIssueSheetState()
        state = ready.copy(detail = ready.detail.copy(comments = ready.detail.comments.filter { it.id == MINE_ID }))

        assertDeletingLandsOn(MINE_ID, card = MINE, landsOn = { row(R.string.tv_issue_reply_on_phone) })
    }

    @Test
    fun deletingTheIssueAsksFirst() {
        setSheet()
        button(R.string.issue_delete).requestFocus()

        composeTestRule.press(Key.DirectionCenter)
        assertEquals(0, issueDeletes)
        confirm(R.string.issue_delete)

        assertEquals(1, issueDeletes)
    }

    @Test
    fun resolveRunsAtOnce() {
        setSheet()
        button(R.string.issue_action_resolve).requestFocus()

        composeTestRule.press(Key.DirectionCenter)

        assertEquals(1, toggles)
    }

    @Test
    fun openInTheMediaServerOpensIt() {
        setSheet()
        button(openInJellyfin()).requestFocus()

        composeTestRule.press(Key.DirectionCenter)

        assertEquals(1, opened)
    }

    @Test
    fun withNoAppToOpenThereIsNoOpenButton() {
        setSheet(appInstalled = false)

        composeTestRule.onAllNodes(hasContentDescription(openInJellyfin())).assertCountEquals(0)
    }

    private fun assertDeletingLandsOn(
        commentId: Int,
        card: String,
        landsOn: () -> SemanticsNodeInteraction,
    ) {
        setSheet()
        card(card).requestFocus()
        composeTestRule.press(Key.DirectionCenter)
        row(R.string.issue_delete_comment).requestFocus()
        composeTestRule.press(Key.DirectionCenter)
        confirm(R.string.issue_delete_comment)
        assertEquals(listOf(commentId), commentDeletes)

        val ready = state as IssueDetailUiState.Ready
        state = ready.copy(detail = ready.detail.copy(comments = ready.detail.comments.filterNot { it.id == commentId }))
        composeTestRule.waitForIdle()
        composeTestRule.settleShellHandOff()

        landsOn().assertIsFocused()
    }

    private fun setSheet(appInstalled: Boolean = true) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvIssueSheet(
                    state = state,
                    actions =
                        TvIssueSheetActions(
                            onDismiss = { dismissals++ },
                            onRetry = {},
                            onToggleStatus = { toggles++ },
                            onDeleteIssue = { issueDeletes++ },
                            onDeleteComment = { commentDeletes += it },
                            onOpenInMediaServer = if (appInstalled) ({ opened++ }) else null,
                        ),
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.settleShellHandOff()
    }

    private fun openInJellyfin() = string(R.string.open_in_named, "Jellyfin")

    private fun card(text: String) =
        composeTestRule.onNode(
            isFocusable() and hasText(text, substring = true) and !hasAnyAncestor(isDialog()),
        )

    private fun row(label: Int) =
        composeTestRule.onNode(
            isFocusable() and (hasText(string(label)) or hasAnyDescendant(hasText(string(label)))),
        )

    private fun button(label: Int) = button(string(label))

    private fun button(label: String) =
        composeTestRule.onNode(isFocusable() and (hasContentDescription(label) or hasText(label) or hasAnyDescendant(hasText(label))))

    /** Presses the confirm answer on the open dialog, not the row or button of the same name that opened it. */
    private fun confirm(label: Int) {
        composeTestRule
            .onAllNodes(hasText(string(label)) and isFocusable())
            .filterToOne(hasAnyAncestor(isDialog()))
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }
}
