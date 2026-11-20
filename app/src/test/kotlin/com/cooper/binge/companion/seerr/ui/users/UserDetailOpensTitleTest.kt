package com.cooper.binge.companion.seerr.ui.users

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A card in a user's watchlist hands its title to whatever opens titles: its kind, its TMDB id and the title's address on the server. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class UserDetailOpensTitleTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `a watchlist card opens its title with its kind, id and server address`() {
        val opened = mutableListOf<Triple<TitleKind, Int, String>>()
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalOpenTitle provides { kind, id, url -> opened += Triple(kind, id, url) }) {
                    UserDetailScreen(
                        state = UserDetailUiState.Ready(detail = detail()),
                        requests = flowOf(PagingData.from(emptyList())),
                        events = emptyFlow(),
                        actions =
                            UserDetailActions(
                                onBack = {},
                                onRetry = {},
                                onOpenRequest = {},
                                onOpenSettings = {},
                                onDeleteUser = {},
                                onOpenWatchlist = null,
                            ),
                    )
                }
            }
        }

        rule.onNodeWithText("Metropolis").performClick()

        assertEquals(listOf(Triple(TitleKind.Movie, 19, "https://seerr.example/movie/19")), opened)
    }

    private fun detail(): UserDetail =
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
            watchlist = listOf(TitleCardItem(tmdbId = 19, mediaType = RequestMediaType.Movie, title = "Metropolis", posterUrl = null)),
            isSelf = false,
            canEditSettings = false,
            canDelete = false,
            serverUrl = "https://seerr.example/",
            webUrl = "https://seerr.example/users/7",
            watchlistCount = null,
            mayOpenWatchlist = false,
        )
}
