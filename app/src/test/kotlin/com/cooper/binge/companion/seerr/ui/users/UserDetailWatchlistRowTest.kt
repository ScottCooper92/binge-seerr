package com.cooper.binge.companion.seerr.ui.users

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The viewer's own page has a Watchlist row with the list's size, which opens the grid; no other page has one. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class UserDetailWatchlistRowTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private var opened = 0

    private fun show(
        mayOpen: Boolean,
        count: Int?,
        discovery: Boolean = true,
    ) = rule.setContent {
        CompositionLocalProvider(LocalDiscoveryEnabled provides discovery) {
            SeerrTheme {
                UserDetailScreen(
                    state = UserDetailUiState.Ready(detail = detail(mayOpen, count)),
                    requests = flowOf(PagingData.from(emptyList())),
                    events = emptyFlow(),
                    actions =
                        UserDetailActions(
                            onBack = {},
                            onRetry = {},
                            onOpenRequest = {},
                            onOpenSettings = {},
                            onDeleteUser = {},
                            onOpenWatchlist = { opened++ },
                        ),
                )
            }
        }
    }

    @Test
    fun `the row names the list's size and opens it`() {
        show(mayOpen = true, count = 12)

        rule.onNodeWithText("Watchlist").assertIsDisplayed()
        rule.onNodeWithText("12 titles").assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `a single title reads in the singular`() {
        show(mayOpen = true, count = 1)

        rule.onNodeWithText("1 title").assertIsDisplayed()
    }

    @Test
    fun `where the size is unknown the row has no count and still opens`() {
        show(mayOpen = true, count = null)

        rule.onNodeWithText("Watchlist").assertIsDisplayed().performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `a page that is not the viewer's own has no row`() {
        show(mayOpen = false, count = 12)

        rule.onNodeWithText("Watchlist").assertDoesNotExist()
    }

    @Test
    fun `with discovery off the viewer's own page has no row`() {
        show(mayOpen = true, count = 12, discovery = false)

        rule.onNodeWithText("Watchlist").assertDoesNotExist()
    }

    private fun detail(
        mayOpen: Boolean,
        count: Int?,
    ): UserDetail =
        UserDetail(
            item =
                UserItem(
                    id = 7,
                    name = "Ada Lovelace",
                    email = "ada@example.com",
                    handle = "ada",
                    avatarUrl = null,
                    origin = UserOrigin.Plex,
                    permissions = ManageablePermission.Admin.bit,
                    requestCount = 1,
                    createdAtMillis = null,
                ),
            permissions = setOf(ManageablePermission.Admin),
            quota = null,
            watch = null,
            watchlist = emptyList(),
            isSelf = mayOpen,
            canEditSettings = false,
            canDelete = false,
            serverUrl = "https://seerr.example/",
            webUrl = "https://seerr.example/users/7",
            watchlistCount = count,
            mayOpenWatchlist = mayOpen,
        )
}
