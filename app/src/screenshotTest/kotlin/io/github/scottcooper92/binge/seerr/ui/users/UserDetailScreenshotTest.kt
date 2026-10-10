package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** No Ready-state phone screenshot coverage existed for this screen before [manageable]. */
class UserDetailScreenshotTest {
    /** The Loading arm (#373): the profile, and the Requests section header and rows. The stat row is conditional, so not reserved (#687). */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = UserDetailSkeleton()

    /** The layout, once: a manager's own account, with quota, permissions and watch data populated. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun manageable() = Frame(manageableUserDetail())

    /** A row a list already had, before the fetch lands (#1053): the profile and permissions, with no quota or watch data guessed. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun seeded() = Frame(UserDetailUiState.Seeded(manageableUserDetail().item))

    /** A viewer the server would refuse this user's requests: the page ends at the profile's own sections, with no Requests header. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readOnly() = Frame(manageableUserDetail().copy(canViewRequests = false, canEditSettings = false, canDelete = false))
}

@Composable
private fun Frame(detail: UserDetail) = Frame(UserDetailUiState.Ready(detail = detail))

@Composable
private fun Frame(state: UserDetailUiState) {
    UserDetailScreen(
        state = state,
        // See manageableUserDetail()'s KDoc: LazyPagingItems never leaves Loading in a static frame.
        requests = flowOf(PagingData.from(emptyList())),
        events = emptyFlow(),
        actions = UserDetailActions(onBack = {}, onRetry = {}, onOpenRequest = {}, onOpenSettings = {}, onDeleteUser = {}),
    )
}
