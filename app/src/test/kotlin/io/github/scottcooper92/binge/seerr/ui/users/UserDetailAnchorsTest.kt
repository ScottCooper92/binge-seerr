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
 * The resolved user page carries the anchors [UserDetailSkeleton] declares, so a geometry check can
 * compare the two once the harness is shared (#572). An anchor missing here would read as "never
 * compared" rather than as a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class UserDetailAnchorsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test
    fun `the resolved page tags the profile and stats the skeleton reserves`() {
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

        rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.PROFILE)).assertIsDisplayed()
        rule.onNodeWithTag(LayoutAnchors.section(LayoutAnchors.Detail.STATS)).assertIsDisplayed()
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
