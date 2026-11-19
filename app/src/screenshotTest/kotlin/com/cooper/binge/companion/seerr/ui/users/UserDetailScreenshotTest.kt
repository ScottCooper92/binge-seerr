package com.cooper.binge.companion.seerr.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrFontScalePreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
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

    /** The viewer's own page on a server with a watchlist: a Watchlist row with the list's size, above the sections, that opens the grid. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun ownWatchlist() = Frame(manageableUserDetail().copy(isSelf = true, mayOpenWatchlist = true, watchlistCount = 12))

    /** The same row where the server gave no size: the label alone. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun ownWatchlistNoCount() = Frame(manageableUserDetail().copy(isSelf = true, mayOpenWatchlist = true, watchlistCount = null))

    /** The row on its own at font scale 2, with a count that has a separator: the page would scroll it out of the frame. */
    @PreviewTest
    @SeerrFontScalePreviews
    @Composable
    fun ownWatchlistLargeFonts() =
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(vertical = 16.dp)) {
            WatchlistRow(count = 1234, onOpen = {}, modifier = Modifier.padding(horizontal = 16.dp))
        }

    /** The row in Spanish, whose plural for a million reads "1.000.000 de títulos" (the `many` form). */
    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun ownWatchlistSpanish() = Frame(manageableUserDetail().copy(isSelf = true, mayOpenWatchlist = true, watchlistCount = 1_000_000))

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
        actions =
            UserDetailActions(
                onBack = {},
                onRetry = {},
                onOpenRequest = {},
                onOpenSettings = {},
                onDeleteUser = {},
                onOpenWatchlist = {},
            ),
    )
}
