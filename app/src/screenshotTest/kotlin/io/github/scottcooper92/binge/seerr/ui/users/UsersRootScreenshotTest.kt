package io.github.scottcooper92.binge.seerr.ui.users

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The users browser's root: the bar and its sort and add actions, and the selection bar a long press
 * opens. The rows are paged, and `LazyPagingItems` never leaves loading in a static frame (see
 * `UserDetailScreenshotTest`), so every frame shows the list as its skeleton.
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

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(UsersUiState.Loading)
}

private fun ready(
    canAdmit: Boolean,
    selection: Set<Int> = emptySet(),
) = UsersUiState.Ready(sort = UserSort.Created, selection = selection, edit = null, offered = emptyList(), canAdmit = canAdmit)

@Composable
private fun Frame(state: UsersUiState) {
    UsersScreen(
        state = state,
        users = flowOf(PagingData.from(emptyList())),
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
                admission = UserAdmissionActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
            ),
    )
}
