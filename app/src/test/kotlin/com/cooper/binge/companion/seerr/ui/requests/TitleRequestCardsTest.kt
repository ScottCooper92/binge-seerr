package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** The title level's request cards: their order, which one waits on a decision, and the bar's own gate. */
@RunWith(RobolectricTestRunner::class)
class TitleRequestCardsTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private fun summary(
        id: Int,
        status: SeerrRequestStatusCode?,
    ) = RequestSummary(id, status, "User $id", null, false, emptyList())

    @Test
    fun `cards run pending then approved then completed then declined or failed`() {
        val order =
            listOf(
                summary(1, SeerrRequestStatusCode.Declined),
                summary(2, SeerrRequestStatusCode.Completed),
                summary(3, SeerrRequestStatusCode.Approved),
                summary(4, SeerrRequestStatusCode.Pending),
                summary(5, SeerrRequestStatusCode.Failed),
            ).inCardOrder().map { it.id }
        assertEquals(listOf(4, 3, 2, 5, 1), order)
    }

    @Test
    fun `Spirited Away puts the pending 4K request before the completed standard one`() {
        val order = listOf(summary(3, SeerrRequestStatusCode.Completed), summary(9, SeerrRequestStatusCode.Pending)).inCardOrder()
        assertEquals(listOf(9, 3), order.map { it.id })
    }

    @Test
    fun `Dune's three requests are ordered newest first within a status`() {
        val order =
            listOf(
                summary(1, SeerrRequestStatusCode.Completed),
                summary(2, SeerrRequestStatusCode.Completed),
                summary(3, SeerrRequestStatusCode.Pending),
            ).inCardOrder()
        assertEquals(listOf(3, 2, 1), order.map { it.id })
    }

    @Test
    fun `only a pending request offers a decision`() {
        assertTrue(summary(1, SeerrRequestStatusCode.Pending).offersDecision())
        assertFalse(summary(1, SeerrRequestStatusCode.Approved).offersDecision())
        assertFalse(summary(1, SeerrRequestStatusCode.Completed).offersDecision())
        assertFalse(summary(1, SeerrRequestStatusCode.Declined).offersDecision())
    }

    @Test
    fun `the decision bar shows for a viewer who may approve and not for one who may not`() {
        val managers = mutableStateOf(true)
        composeTestRule.setContent {
            BingeExpressiveTheme {
                CardDecisionBar(RequestActions(canApprove = managers.value, canDecline = managers.value), onApprove = {}, onDecline = {})
            }
        }
        composeTestRule.onNodeWithText(string(AdminR.string.request_approve)).assertExists()
        composeTestRule.runOnIdle { managers.value = false }
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(string(AdminR.string.request_approve)).assertCountEquals(0)
    }

    @Test
    fun `a card tap opens that request`() {
        var opened: Int? = null
        composeTestRule.setContent {
            BingeExpressiveTheme {
                RequestCardsSection(
                    RequestCardsModel(
                        summaries = listOf(summary(3, SeerrRequestStatusCode.Completed), summary(4, SeerrRequestStatusCode.Approved)),
                        ownId = 3,
                        onOpen = { opened = it },
                        snackbar = SnackbarHostState(),
                        onChanged = {},
                    ),
                )
            }
        }
        composeTestRule.onNodeWithText("User 3").performClick()
        assertEquals(3, opened)
    }
}
