package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.ui.tv.requests.consistentWith
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Right after sign-in the server's tally and the row's first page arrive separately. A heading must never read zero beside
 * a card that is already showing (#1907).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvRequestsRowCountTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private var counts by mutableStateOf(RequestCounts(total = 0, pending = 0, approved = 0, processing = 0, available = 0))

    @Test
    fun aCountBelowTheCardsShownIsHeldBackUntilItCoversThem() {
        setBoard()

        composeTestRule.onNodeWithText("Available (0)").assertDoesNotExist()
        composeTestRule.onNodeWithText("Available").assertExists()

        counts = counts.copy(available = 1)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Available (1)").assertExists()
    }

    @Test
    fun aCountThatCoversTheRowIsKeptAndAnUnknownOneStaysUnknown() {
        assertEquals(3, 3.consistentWith(loaded = 2))
        assertEquals(2, 2.consistentWith(loaded = 2))
        assertNull(0.consistentWith(loaded = 1))
        assertNull(null.consistentWith(loaded = 1))
    }

    private fun setBoard() {
        composeTestRule.setContent {
            BingeTvTheme {
                TvRequestsRowsBoard(
                    state =
                        RequestsUiState.Ready(
                            filter = RequestFilter.All,
                            sort = RequestSort.Added,
                            counts = counts,
                            scope =
                                ModerationScope(
                                    permissions = SeerrPermissions(canManageRequests = true),
                                    currentUserId = 7,
                                    hasBlocklist = false,
                                ),
                            listVersion = 0,
                        ),
                    rowsFor = { filter ->
                        if (filter == RequestFilter.Available) {
                            val item = request(1, "Heat", SeerrRequestStatusCode.Approved)
                            TvPagedRows(count = 1, at = { item.takeIf { _ -> it == 0 } }, refresh = TvLoadPhase.Idle)
                        } else {
                            TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Idle)
                        }
                    },
                    actions = TvRequestsActions(onOpenDetail = {}, onSeeAll = {}, onRetryLoad = {}, onRetryScope = {}),
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
