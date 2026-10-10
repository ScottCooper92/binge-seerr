package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import io.github.scottcooper92.binge.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The request sheet's "Block this title" row is one switch for a screen reader: a single `Role.Switch`
 * node that reports on and off, not a button with a second, unlabelled switch inside it (#630).
 */
@RunWith(RobolectricTestRunner::class)
class RequestBlockTitleSwitchTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private val label =
        string(R.string.request_block_title)
    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
    private val hasToggleState = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)

    private var blockTitle by mutableStateOf(false)
    private val changes = mutableListOf<Boolean>()

    private fun setContent() {
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestActionsContent(
                    model = RequestSheetModel(item = item(), actions = RequestActions(canDecline = true, canBlock = true)),
                    callbacks = RequestSheetCallbacks(onApprove = {}, onRetry = {}, onDecline = {}, onRemove = {}),
                    blockTitle = blockTitle,
                    onBlockTitleChange = {
                        changes += it
                        blockTitle = it
                    },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `the row is one Switch node that reports on and off`() {
        setContent()

        composeTestRule.onNodeWithText(label).assert(isSwitch).assertIsOff()
        composeTestRule.onNodeWithText(label).performClick()
        assertEquals(listOf(true), changes)
        composeTestRule.onNodeWithText(label).assertIsOn()
    }

    @Test
    fun `there is no second toggleable inside the row`() {
        setContent()

        composeTestRule.onAllNodes(hasToggleState, useUnmergedTree = true).assertCountEquals(1)
    }

    private fun item() =
        RequestItem(
            id = 11,
            tmdbId = 1396,
            mediaType = RequestMediaType.Tv,
            title = "Breaking Bad",
            posterUrl = null,
            year = "2008",
            requestedBy = null,
            requestedById = null,
            requestedAtMillis = null,
            status = null,
            mediaStatus = null,
            download = null,
            seasonNumbers = emptyList(),
            is4k = false,
        )
}
