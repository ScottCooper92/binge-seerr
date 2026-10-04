package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.paging.PagingData
import com.binge.designsystem.layout.LayoutAnchors
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

/**
 * The resolved user page carries the anchor [UserDetailSkeleton] declares, `PROFILE`, so a geometry
 * check can compare the two once the harness is shared (#572). An anchor missing here would read as
 * "never compared" rather than as a failure. The skeleton reserves no stat row, so `STATS` is
 * checked on the page alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class UserDetailAnchorsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the resolved page tags the profile the skeleton reserves`() {
        setResolvedPage()

        rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE)).assertIsDisplayed()
    }

    @Test
    fun `the resolved page tags the stat row for a user with watch data`() {
        setResolvedPage()

        rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.STATS)).assertIsDisplayed()
    }

    private fun setResolvedPage() {
        rule.setContent {
            SeerrTheme {
                UserDetailScreen(
                    state = UserDetailUiState.Ready(detail = detail()),
                    requests = flowOf(PagingData.from(emptyList())),
                    events = emptyFlow(),
                    actions = UserDetailActions(onBack = {}, onRetry = {}, onOpenRequest = {}, onOpenSettings = {}, onDeleteUser = {}),
                )
            }
        }
    }

    /** The common shape: a user with watch data, which is when the page shows the stat row. */
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
