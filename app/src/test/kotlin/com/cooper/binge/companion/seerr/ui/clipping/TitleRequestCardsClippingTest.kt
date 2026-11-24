package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.RequestCardsModel
import com.cooper.binge.companion.seerr.ui.requests.RequestCardsSection
import com.cooper.binge.companion.seerr.ui.requests.RequestSummary
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import com.cooper.binge.companion.seerr.core.ui.R as UiR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The request cards on a title's Manage sheet keep the status chip's word on one line at the largest fonts, and the name and
 * date take what room is left. Measured as a line count and bounds against the card, never `hasVisualOverflow`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleRequestCardsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 1.3f)
    fun `at a font scale of 1 point 3 the status chip keeps Approved on one line`() = check()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi", fontScale = 2f)
    fun `at a font scale of 2 the status chip keeps Approved on one line`() = check()

    private fun check() {
        val requests =
            listOf(
                RequestSummary(1, SeerrRequestStatusCode.Approved, "A Requester With A Rather Long Display Name", 0L, false, emptyList()),
                RequestSummary(2, SeerrRequestStatusCode.Approved, "Another Person With A Long Name", 0L, true, listOf(1, 2, 3, 4)),
            )
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                RequestCardsSection(RequestCardsModel(requests, ownId = 0, onOpen = {}, snackbar = SnackbarHostState(), onChanged = {}))
            }
        }
        rule.waitForIdle()

        val root = rule.onRoot().getUnclippedBoundsInRoot()
        val label = string(UiR.string.request_state_approved)
        chips(label).forEach { chip ->
            assertEquals("\"$label\" wraps onto more than one line", 1, chip.lineCount())
            val bounds = chip.getUnclippedBoundsInRoot()
            assertTrue("\"$label\" is wider than a letter", bounds.width > 16.dp)
            assertTrue("\"$label\" runs past the card's end", bounds.right <= root.right)
        }
    }

    private fun chips(label: String): List<SemanticsNodeInteraction> {
        val nodes = rule.onAllNodesWithText(label, useUnmergedTree = true)
        return nodes.fetchSemanticsNodes().indices.map { nodes[it] }
    }

    private fun SemanticsNodeInteraction.lineCount(): Int {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first().lineCount
    }
}
