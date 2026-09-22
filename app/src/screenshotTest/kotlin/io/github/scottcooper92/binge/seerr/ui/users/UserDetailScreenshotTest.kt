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
    /** The Loading arm (#373): the profile, the two unconditional stats, and the Requests section header and rows. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = UserDetailSkeleton()

    /** The layout, once: a manager's own account, with quota, permissions and watch data populated. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun manageable() = Frame(manageableUserDetail())
}

@Composable
private fun Frame(detail: UserDetail) {
    UserDetailScreen(
        state = UserDetailUiState.Ready(detail = detail),
        // See manageableUserDetail()'s KDoc: LazyPagingItems never leaves Loading in a static frame.
        requests = flowOf(PagingData.from(emptyList())),
        events = emptyFlow(),
        actions = UserDetailActions(onBack = {}, onRetry = {}, onOpenRequest = {}, onOpenSettings = {}, onDeleteUser = {}),
    )
}
