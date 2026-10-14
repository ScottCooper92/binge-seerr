package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.tv.hub.TvAccountSheet
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The account sheet: who is signed in and what is left of each quota as one card that holds focus, with no Close, and Back
 * the way out; Sign out last for a session sign-in, asking first. When the hub could not resolve the account, a Retry
 * rather than a spinner with no way out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvAccountSheetTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var retries = 0
    private var dismissals = 0
    private var signOuts = 0

    @Test
    fun aFailedAccountOffersRetryAndItTakesFocus() {
        setSheet(detail = null, accountFailed = true)

        retryRow().assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, retries)
        assertEquals(0, dismissals)
    }

    @Test
    fun anAccountStillLoadingHoldsFocusOnTheLoadingTextAndHasNoClose() {
        setSheet(detail = null, accountFailed = false)

        // Focus waits on the text itself, so a screen reader reads it out rather than landing on silence.
        composeTestRule.onNodeWithText(string(R.string.tv_loading)).assertIsFocused()
        composeTestRule.onNode(hasText(string(R.string.hub_retry))).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.tv_sheet_close)).assertDoesNotExist()
    }

    @Test
    fun theProfileAndEachQuotaAreOneCardThatTakesFocus() {
        setSheet(detail = UserDetailUiState.Ready(detail()), accountFailed = false)

        val card = composeTestRule.onNode(hasText("Scott") and isFocusable())
        card.assertIsFocused().assertHasNoClickAction()
        card.assert(hasText("scott@example.com · scott"))
        card.assert(hasText("7 of 10 left", substring = true))
        card.assert(hasText(string(R.string.hub_quota_unlimited), substring = true))
        composeTestRule.onNodeWithText(string(R.string.tv_sheet_close)).assertDoesNotExist()
    }

    /** The cached profile shows before the quota loads, with placeholders where the numbers will land (#1053). */
    @Test
    fun aSeededProfileHoldsThePlaceOfItsQuota() {
        setSheet(detail = UserDetailUiState.Seeded(user()), accountFailed = false)

        composeTestRule.onNode(hasText("Scott") and isFocusable()).assert(hasText(string(R.string.hub_stat_placeholder), substring = true))
    }

    @Test
    fun aKeyConnectionHasNoSignOut() {
        setSheet(detail = UserDetailUiState.Ready(detail()), accountFailed = false, canSignOut = false)

        composeTestRule.onNodeWithText(string(R.string.tv_account_sign_out)).assertDoesNotExist()
    }

    /** Sign out is last and destructive, and asks before it ends the session. */
    @Test
    fun signOutIsLastAndAsksFirst() {
        setSheet(detail = UserDetailUiState.Ready(detail()), accountFailed = false)
        press(Key.DirectionDown)
        signOutRow().assertIsFocused()

        press(Key.DirectionCenter)
        composeTestRule.onNodeWithText(string(R.string.tv_account_sign_out_title)).assertExists()
        assertEquals(0, signOuts)

        composeTestRule
            .onAllNodes(hasText(string(R.string.tv_account_sign_out)) and isFocusable())
            .filterToOne(hasAnyAncestor(isDialog()))
            .requestFocus()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()

        assertEquals(1, signOuts)
    }

    private fun setSheet(
        detail: UserDetailUiState?,
        accountFailed: Boolean,
        canSignOut: Boolean = true,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvAccountSheet(
                    detail = detail,
                    accountFailed = accountFailed,
                    onRetry = { retries++ },
                    onDismissRequest = { dismissals++ },
                    onSignOut = if (canSignOut) ({ signOuts++ }) else null,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun retryRow() = composeTestRule.onNode(hasText(string(R.string.hub_retry)) and isFocusable())

    private fun signOutRow() =
        composeTestRule.onAllNodes(hasText(string(R.string.tv_account_sign_out)) and isFocusable()).filterToOne(!hasAnyAncestor(isDialog()))

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
