package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
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

/**
 * The user skeleton reserves the profile the resolved page fills (#572), swapped by the screen itself. It
 * reserves no stat row: the page shows one only with watch data, so the skeleton matches a user without it (#687).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserDetailSkeletonGeometryTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the skeleton holds the profile where the page puts it`() {
        val anchors = listOf(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE))
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

    @Test
    fun `the skeleton reserves no stat row`() {
        rule.setContent { SeerrTheme { UserDetailSkeleton() } }

        rule.onAllNodesWithTag(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(LayoutAnchors.section(LayoutAnchors.Detail.STATS), useUnmergedTree = true).assertCountEquals(0)
    }

    /** A user with no watch data: the page draws no stat row, and nothing the skeleton reserves goes unfilled. */
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
            watchlist = emptyList(),
            isSelf = false,
            canEditSettings = false,
            canDelete = false,
            serverUrl = "https://seerr.example/",
            webUrl = "https://seerr.example/users/7",
        )
}
