package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.state.RestingPull
import io.github.scottcooper92.binge.seerr.ui.state.refreshingRows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The users browser's root: the bar and its sort and add actions, and the selection bar a long press
 * opens. The rows are paged, and `LazyPagingItems` never leaves loading in a static frame (see
 * `UserDetailScreenshotTest`), so the frames show the list as its skeleton. The refreshing frame is the exception:
 * its rows are replayed, so they show.
 */
class UsersRootScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun readyLayout() = Frame(ready(canAdmit = true))

    /** Without the manager's permission the add action is not offered. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readyWithoutAdd() = Frame(ready(canAdmit = false))

    /** Two rows ticked: the bar becomes the selection's, with the bulk edit. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun selecting() = Frame(ready(canAdmit = true, selection = setOf(2, 3)))

    /** A pull refreshing the rows on screen: the spinner rests below the bar, and no bar sits over the rows. */
    @OptIn(ExperimentalMaterial3Api::class)
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun refreshing() {
        val rows = remember { refreshingRows(previewUsers()) }
        Frame(ready(canAdmit = true), rows, RestingPull)
    }

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(UsersUiState.Loading)

    /** The signed-in user could not be read: the page says so, with a retry, rather than a list with nothing offered. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun scopeUnreachable() = Frame(UsersUiState.Error(SeerrError.Unreachable))
}

private fun ready(
    canAdmit: Boolean,
    selection: Set<Int> = emptySet(),
) = UsersUiState.Ready(sort = UserSort.Created, selection = selection, edit = null, offered = emptyList(), canAdmit = canAdmit)

/** An admin and two requesters, one from each kind of sign-in the row tells apart. */
private fun previewUsers() =
    listOf(
        UserItem(
            1,
            "Scott",
            "scott@example.org",
            "scott",
            null,
            UserOrigin.Local,
            permissions = 2,
            requestCount = 42,
            createdAtMillis = null,
        ),
        UserItem(
            2,
            "Priya",
            "priya@example.org",
            "priya",
            null,
            UserOrigin.Plex,
            permissions = 32,
            requestCount = 7,
            createdAtMillis = null,
        ),
        UserItem(3, "Marcus", null, "marcus", null, UserOrigin.Jellyfin, permissions = 32, requestCount = 0, createdAtMillis = null),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Frame(
    state: UsersUiState,
    rows: Flow<PagingData<UserItem>> = remember { flowOf(PagingData.from(emptyList())) },
    pullState: PullToRefreshState? = null,
) {
    UsersScreen(
        state = state,
        users = rows,
        pullState = pullState,
        events = emptyFlow(),
        actions =
            UsersActions(
                onBack = {},
                onSortChange = {},
                onOpen = {},
                onToggleSelected = {},
                onClearSelection = {},
                onStartBulkEdit = {},
                onTogglePermission = {},
                onApplyBulkEdit = {},
                onCancelBulkEdit = {},
                onRetryLoad = {},
                admission = UserAdmissionActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
            ),
    )
}
