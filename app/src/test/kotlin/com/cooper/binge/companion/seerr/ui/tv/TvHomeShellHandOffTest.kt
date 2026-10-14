package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.requests.ModerationScope
import com.cooper.binge.companion.seerr.ui.requests.RequestCounts
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestSort
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HEAT = "Heat"
private const val BEAR = "The Bear"

/** Where focus lands as the bar hands it around, in the real shell: Down from the bar returns to the card the user came from. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvHomeShellHandOffTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    @Test
    fun downFromTheTopRowReturnsToTheCardTheUserCameFrom() {
        setHomeWithRequests()
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        composeTestRule.press(Key.DirectionUp)
        focusedTopRowButton().assertExists()
        composeTestRule.press(Key.DirectionDown)

        card(HEAT).assertIsFocused()
    }

    private fun setHomeWithRequests() {
        val requests = listOf(request(1, HEAT, SeerrRequestStatusCode.Pending), request(2, BEAR, SeerrRequestStatusCode.Approved))
        composeTestRule.setContent {
            BingeTvTheme {
                var tab by rememberSaveable { mutableStateOf(TvDestination.Hub) }
                var sheet by rememberSaveable { mutableStateOf<TvDestination?>(null) }
                TvHomeShell(
                    tab = tab,
                    sheet = sheet,
                    onSelectTab = { tab = it },
                    onOpenSheet = { sheet = it },
                    onCloseSheet = { sheet = null },
                ) { shown, _ ->
                    if (shown == TvDestination.Hub) {
                        TvRequestsRowsBoard(
                            state =
                                RequestsUiState.Ready(
                                    filter = RequestFilter.All,
                                    sort = RequestSort.Added,
                                    counts = RequestCounts(total = 2, pending = 1, approved = 1, processing = 0, available = 0),
                                    scope =
                                        ModerationScope(
                                            SeerrPermissions(canManageRequests = true),
                                            currentUserId = 7,
                                            hasBlocklist = false,
                                        ),
                                    listVersion = 0,
                                ),
                            rowsFor = { filter ->
                                val items =
                                    when (filter) {
                                        RequestFilter.Pending -> requests.take(1)
                                        RequestFilter.Approved -> requests.drop(1)
                                        else -> emptyList()
                                    }
                                TvPagedRows(count = items.size, at = { items.getOrNull(it) })
                            },
                            actions = TvRequestsActions(onOpenDetail = {}, onSeeAll = {}, onRetryLoad = {}, onRetryScope = {}),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun card(title: String) = composeTestRule.onNode(hasContentDescription(title) and isFocusable())

    /** Up from Home lands on the current tab. */
    private fun focusedTopRowButton() = composeTestRule.onNode(hasContentDescription(string(R.string.tv_rail_hub)) and isFocused())
}
