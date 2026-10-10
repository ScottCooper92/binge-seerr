package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvAccountBoard
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

/** The account page when the hub could not resolve the account: a retry, not a spinner with no way out. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvAccountBoardUnresolvedTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    @Test
    fun aFailedAccountOffersRetry() {
        var retries = 0
        composeTestRule.setContent {
            BingeTvTheme {
                TvAccountBoard(
                    detail = null,
                    requests = TvPagedRows(count = 0, at = { null }),
                    onOpenRequest = {},
                    onRetry = { retries++ },
                    onRetryRequests = {},
                    overlayOpen = false,
                    accountFailed = true,
                )
            }
        }

        composeTestRule.onNode(hasText("Retry", ignoreCase = true)).assertIsDisplayed().performClick()

        assertEquals(1, retries)
    }

    /** A failed page of the user's own requests reads as a failure with a retry, not as no requests (#1035). */
    @Test
    fun aFailedRequestsLoadOffersRetry() {
        var retries = 0
        composeTestRule.setContent {
            BingeTvTheme {
                TvAccountBoard(
                    detail = UserDetailUiState.Seeded(sampleUser()),
                    requests = TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed),
                    onOpenRequest = {},
                    onRetry = {},
                    onRetryRequests = { retries++ },
                    overlayOpen = false,
                )
            }
        }

        composeTestRule
            .onNode(hasText("Retry", ignoreCase = true))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, retries)
    }

    private fun sampleUser() =
        UserItem(
            id = 7,
            name = "Scott",
            email = "scott@example.com",
            handle = "scott",
            avatarUrl = null,
            origin = UserOrigin.Jellyfin,
            permissions = 2,
            requestCount = 3,
            createdAtMillis = 0L,
        )
}
