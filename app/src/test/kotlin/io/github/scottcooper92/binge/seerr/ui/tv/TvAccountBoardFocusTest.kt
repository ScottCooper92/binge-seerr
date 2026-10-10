package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuota
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuotaBucket
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvAccountBoard
import io.github.scottcooper92.binge.seerr.ui.users.UserDetail
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailUiState
import io.github.scottcooper92.binge.seerr.ui.users.UserItem
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HEAT = "Heat"
private const val BEAR = "The Bear"

/**
 * The account page under a real D-pad: the profile first, down to the user's own requests, a card to its detail
 * page, and focus coming back to the card once that page closes. The requests come from a plain list decomposed
 * the way the entry decomposes the pager, so nothing here waits on paging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvAccountBoardFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val opened = mutableListOf<Int>()
    private var openRequestId: Int? by mutableStateOf(null)

    @Test
    fun theProfileIsWhereTheRemoteStartsAndDownReachesTheRequests() {
        setBoard()
        profile().requestFocus()
        composeTestRule.waitForIdle()
        profile().assertIsFocused()

        press(Key.DirectionDown)

        card(HEAT).assertIsFocused()
    }

    @Test
    fun okOnARequestOpensItsDetailPageAndTheCardRegainsFocusOnceItCloses() {
        setBoard()
        card(HEAT).requestFocus()
        composeTestRule.waitForIdle()

        press(Key.DirectionRight)
        card(BEAR).assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(2), opened)

        openRequestId = null
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        card(BEAR).assertIsFocused()
    }

    @Test
    fun aUserWithNoRequestsHasNoRow() {
        setBoard(requests = emptyList())

        composeTestRule.onNode(hasContentDescription(HEAT)).assertDoesNotExist()
    }

    private fun setBoard(
        requests: List<io.github.scottcooper92.binge.seerr.ui.requests.RequestItem> =
            listOf(request(1, HEAT, SeerrRequestStatusCode.Pending), request(2, BEAR, SeerrRequestStatusCode.Approved)),
    ) {
        val item =
            UserItem(
                id = 7,
                name = "Scott",
                email = "scott@example.com",
                handle = "scott",
                avatarUrl = null,
                origin = UserOrigin.Jellyfin,
                permissions = 2,
                requestCount = requests.size,
                createdAtMillis = null,
            )
        val detail =
            UserDetail(
                item = item,
                permissions = emptySet(),
                quota = HubQuota(movie = HubQuotaBucket(limit = 10, remaining = 7, days = 7), tv = null),
                watch = null,
                watchlist = emptyList(),
                isSelf = true,
                canEditSettings = true,
                canDelete = false,
                serverUrl = "http://seerr.lan:5055",
                webUrl = "http://seerr.lan:5055/users/7",
            )
        composeTestRule.setContent {
            BingeTvTheme {
                TvAccountBoard(
                    detail = UserDetailUiState.Ready(detail),
                    requests = TvPagedRows(count = requests.size, at = { requests.getOrNull(it) }),
                    onOpenRequest = {
                        opened += it.id
                        openRequestId = it.id
                    },
                    onRetry = {},
                    onRetryRequests = {},
                    overlayOpen = openRequestId != null,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun profile() = composeTestRule.onNode(hasText("Scott") and isFocusable())

    private fun card(title: String) = composeTestRule.onNode(hasContentDescription(title, substring = true) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
