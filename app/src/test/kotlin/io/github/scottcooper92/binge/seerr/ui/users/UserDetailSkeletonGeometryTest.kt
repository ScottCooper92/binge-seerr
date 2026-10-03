package io.github.scottcooper92.binge.seerr.ui.users

import androidx.paging.PagingData
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.theme.SeerrTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The user skeleton reserves the profile and the stat row the resolved page fills (#572), swapped by the screen itself. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserDetailSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the skeleton holds the profile and the stats where the page puts them`() {
        val anchors = listOf(LayoutAnchors.Detail.PROFILE, LayoutAnchors.Detail.STATS).map(LayoutAnchors::section)
        rule.assertSkeletonReservesGeometry(anchors) { resolved ->
            SeerrTheme {
                UserDetailScreen(
                    state = if (resolved) UserDetailUiState.Ready(detail = detail()) else UserDetailUiState.Loading,
                    requests = flowOf(PagingData.from(emptyList())),
                    events = emptyFlow(),
                    actions = UserDetailActions(onBack = {}, onRetry = {}, onOpenRequest = {}, onOpenSettings = {}, onDeleteUser = {}),
                )
            }
        }
    }

    /** The common shape: a user with watch data, which is when the page shows the stat row the skeleton reserves. */
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
            watch = UserWatch(playCount = 42, recentlyWatched = emptyList()),
            watchlist = emptyList(),
            isSelf = false,
            canEditSettings = false,
            canDelete = false,
            serverUrl = "https://seerr.example/",
            webUrl = "https://seerr.example/users/7",
        )
}
