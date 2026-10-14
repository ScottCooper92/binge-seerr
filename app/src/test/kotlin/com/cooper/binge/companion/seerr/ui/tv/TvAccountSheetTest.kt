package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.tv.hub.TvAccountSheet
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The account sheet: who is signed in and what is left of each quota as read-outs with Close the one row to press, and, when the
 * hub could not resolve the account, a Retry rather than a spinner with no way out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvAccountSheetTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private var retries = 0
    private var dismissals = 0

    @Test
    fun aFailedAccountOffersRetryAndItTakesFocus() {
        setSheet(detail = null, accountFailed = true)

        retryRow().assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, retries)
        assertEquals(0, dismissals)
    }

    @Test
    fun anAccountStillLoadingOffersOnlyClose() {
        setSheet(detail = null, accountFailed = false)

        composeTestRule.onNodeWithText(string(R.string.tv_loading)).assertExists()
        composeTestRule.onNode(hasText(string(R.string.hub_retry))).assertDoesNotExist()
        closeRow().assertIsFocused()
    }

    @Test
    fun theProfileAndEachQuotaAreReadOutAndCloseDismisses() {
        setSheet(detail = UserDetailUiState.Ready(detail()), accountFailed = false)

        composeTestRule.onNodeWithText("Scott").assertExists()
        composeTestRule.onNodeWithText("scott@example.com").assertExists()
        composeTestRule.onNodeWithText("7 of 10 left", substring = true).assertExists()
        composeTestRule.onNodeWithText(string(R.string.hub_quota_unlimited)).assertExists()
        closeRow().assertIsFocused()

        press(Key.DirectionCenter)

        assertEquals(1, dismissals)
    }

    /** The cached profile shows before the quota loads, with placeholders where the numbers will land (#1053). */
    @Test
    fun aSeededProfileHoldsThePlaceOfItsQuota() {
        setSheet(detail = UserDetailUiState.Seeded(user()), accountFailed = false)

        composeTestRule.onNodeWithText("Scott").assertExists()
        composeTestRule.onAllNodes(hasText(string(R.string.hub_stat_placeholder), substring = true)).assertCountEquals(2)
    }

    private fun setSheet(
        detail: UserDetailUiState?,
        accountFailed: Boolean,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvAccountSheet(
                    detail = detail,
                    accountFailed = accountFailed,
                    onRetry = { retries++ },
                    onDismissRequest = { dismissals++ },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun retryRow() = composeTestRule.onNode(hasText(string(R.string.hub_retry)) and isFocusable())

    private fun closeRow() = composeTestRule.onNode(hasText(string(R.string.tv_sheet_close)) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun user() =
        UserItem(
            id = 7,
            name = "Scott",
            email = "scott@example.com",
            handle = "scott",
            avatarUrl = null,
            origin = UserOrigin.Jellyfin,
            permissions = 2,
            requestCount = 3,
            createdAtMillis = null,
        )

    private fun detail() =
        UserDetail(
            item = user(),
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
}
