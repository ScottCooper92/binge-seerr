package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.tv.hub.ACCOUNT_MENU_LINES_TAG
import com.cooper.binge.companion.seerr.ui.tv.hub.tvAccountMenu
import com.cooper.binge.companion.seerr.ui.users.UserDetail
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The account menu on the bar's avatar (#1916): who is signed in and what is left of each quota, with Sign out last for a
 * session sign-in, asking first; a Retry when the account could not be read; and a menu that holds its height while the
 * quota loads. The menu is the design system's, so its own keys are its tests'; these are what this app puts in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvAccountMenuTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var retries = 0
    private var signOuts = 0
    private var detail by mutableStateOf<UserDetailUiState?>(null)
    private var opened = 0

    @Test
    fun theHeaderNamesTheAccountWithItsRoleAndOrigin() {
        setBar(detail = UserDetailUiState.Ready(detail()))
        focusAvatar()

        composeTestRule.onNodeWithText("Scott", useUnmergedTree = true).assertExists()
        composeTestRule
            .onNode(
                hasText(string(R.string.hub_role_admin) + " · " + string(R.string.user_origin_jellyfin), substring = true),
            ).assertExists()
    }

    @Test
    fun theMenuShowsTheContactLineAndEachQuota() {
        setBar(detail = UserDetailUiState.Ready(detail()))
        focusAvatar()

        composeTestRule.onNodeWithText("scott@example.com · scott").assertExists()
        composeTestRule.onNode(hasText("7 of 10 left", substring = true)).assertExists()
        composeTestRule.onNode(hasText(string(R.string.hub_quota_unlimited), substring = true)).assertExists()
    }

    /** The hook for what is marked visible only while the menu is open is composed then, and not before. */
    @Test
    fun whileOpenIsComposedOnlyWhileTheMenuIsOpen() {
        setBar(detail = UserDetailUiState.Ready(detail()))
        assertEquals(0, opened)

        focusAvatar()
        assertTrue(opened > 0)
    }

    /** The menu opens before the quota has loaded and must not grow when the numbers arrive. */
    @Test
    fun theLinesHoldTheirHeightWhileTheQuotaLoads() {
        setBar(detail = UserDetailUiState.Seeded(user()))
        focusAvatar()
        val loading = composeTestRule.onNodeWithTag(ACCOUNT_MENU_LINES_TAG).getUnclippedBoundsInRoot()
        composeTestRule.onAllNodes(hasText(string(R.string.hub_stat_placeholder), substring = true)).assertCountEquals(2)

        detail = UserDetailUiState.Ready(detail())
        composeTestRule.waitForIdle()

        val loaded = composeTestRule.onNodeWithTag(ACCOUNT_MENU_LINES_TAG).getUnclippedBoundsInRoot()
        assertEquals(loading.height, loaded.height)
    }

    /** Before the account's own read has answered nothing is known of it but a dash, in the same three lines. */
    @Test
    fun anUnreadAccountHoldsTheSameHeightAsALoadedOne() {
        setBar(detail = UserDetailUiState.Loading)
        focusAvatar()
        val loading = composeTestRule.onNodeWithTag(ACCOUNT_MENU_LINES_TAG).getUnclippedBoundsInRoot().height

        detail = UserDetailUiState.Ready(detail())
        composeTestRule.waitForIdle()

        assertEquals(loading, composeTestRule.onNodeWithTag(ACCOUNT_MENU_LINES_TAG).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun aFailedReadOffersRetry() {
        setBar(detail = UserDetailUiState.Error(SeerrError.Unreachable))
        focusAvatar()
        press(Key.DirectionDown)

        retryRow().assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, retries)
    }

    @Test
    fun aKeyConnectionHasNoSignOut() {
        setBar(detail = UserDetailUiState.Ready(detail()), canSignOut = false)
        focusAvatar()

        composeTestRule.onNodeWithText(string(R.string.tv_account_sign_out)).assertDoesNotExist()
    }

    /** Sign out is last and destructive, and asks before it ends the session. */
    @Test
    fun signOutIsLastAndAsksFirst() {
        setBar(detail = UserDetailUiState.Ready(detail()))
        focusAvatar()
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

    @Test
    fun withTheMenuClosedDownFromTheAvatarReachesThePage() {
        setBar(detail = UserDetailUiState.Ready(detail()))
        focusAvatar()
        pressBack()
        avatar().assertIsFocused()

        press(Key.DirectionDown)

        composeTestRule.onNodeWithTag(PAGE).assertIsFocused()
    }

    /** The menu is open while the avatar is focused, with no Select: the remote's arrival alone opens it, as in the other TV app. */
    @Test
    fun arrivingOnTheAvatarFromTheGearOpensTheMenu() {
        setBar(detail = UserDetailUiState.Ready(detail()))
        val gear = composeTestRule.onNode(hasContentDescription(string(TvDestination.Settings.labelRes)) and isFocusable())
        gear.requestFocus()
        composeTestRule.waitForIdle()
        press(Key.DirectionLeft)

        avatar().assertIsFocused()
        composeTestRule.onNodeWithText("Scott", useUnmergedTree = true).assertExists()
    }

    @Test
    fun arrivingOnTheAvatarFromTheLastTabOpensTheMenu() {
        setBar(detail = UserDetailUiState.Ready(detail()), tabs = listOf(TvDestination.Hub, TvDestination.Issues))
        composeTestRule.onNode(hasContentDescription(string(TvDestination.Issues.labelRes)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        press(Key.DirectionRight)

        avatar().assertIsFocused()
        composeTestRule.onNodeWithText("Scott", useUnmergedTree = true).assertExists()
    }

    private fun setBar(
        detail: UserDetailUiState?,
        canSignOut: Boolean = true,
        tabs: List<TvDestination> = emptyList(),
    ) {
        this.detail = detail
        composeTestRule.setContent {
            BingeTvTheme {
                val page = remember { FocusRequester() }
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(PAGE_SIZE)
                            .focusRequester(page)
                            .focusable()
                            .testTag(PAGE),
                    )
                    TvTopBar(
                        tabs = tabs,
                        current = TvDestination.Hub,
                        opener = remember { TvDestination.entries.associateWith { FocusRequester() } },
                        accountOpener = remember { FocusRequester() },
                        down = page,
                        visible = true,
                        account =
                            tvAccountMenu(
                                account = HubAccount(id = 7, name = "Scott", isAdmin = true, avatarUrl = null),
                                detail = this@TvAccountMenuTest.detail,
                                accountFailed = false,
                                onRetry = { retries++ },
                                onSignOut = if (canSignOut) ({ signOuts++ }) else null,
                                demo = false,
                                whileOpen = { SideEffect { opened++ } },
                            ),
                        onFocusedChange = {},
                        onSelect = {},
                        onOpenSettings = {},
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun avatar() = composeTestRule.onNode(hasContentDescription(string(R.string.tv_account)) and isFocusable())

    private fun focusAvatar() {
        avatar().requestFocus()
        composeTestRule.waitForIdle()
    }

    private fun retryRow() = composeTestRule.onNode(hasText(string(R.string.hub_retry)) and isFocusable())

    private fun signOutRow() =
        composeTestRule.onAllNodes(hasText(string(R.string.tv_account_sign_out)) and isFocusable()).filterToOne(!hasAnyAncestor(isDialog()))

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

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

    private companion object {
        const val PAGE = "page"
        val PAGE_SIZE: Dp = 48.dp
    }
}
