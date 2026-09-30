package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * The blocklist browser's root: the search field and, where the server has them, the source chips. The rows
 * are paged, and `LazyPagingItems` never leaves loading in a static frame (see `UserDetailScreenshotTest`),
 * so every frame shows the page as its skeleton; the rows themselves are framed by
 * [BlocklistListScreenshotTest].
 */
class BlocklistRootScreenshotTest {
    /** Seerr 3.0 and later: the search field over the manual and tagged chips. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun withChipsLayout() = Frame(ready(hasFilters = true))

    /** Jellyseerr 2.x has one list, so the search field stands alone. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun withoutChips() = Frame(ready(hasFilters = false))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun searching() = Frame(ready(hasFilters = true, search = "dune"))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = Frame(BlocklistUiState.Loading)

    /** The signed-in user could not be read: the list is not guessed at, and the screen offers a retry. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unreachable() = Frame(BlocklistUiState.Error(SeerrError.Unreachable))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun unauthorized() = Frame(BlocklistUiState.Error(SeerrError.Unauthorized))
}

private fun ready(
    hasFilters: Boolean,
    search: String = "",
) = BlocklistUiState.Ready(
    filter = BlocklistFilter.All,
    search = search,
    counts = BlocklistCounts(all = 9, manual = 6, tagged = 3),
    listVersion = 1,
    hasFilters = hasFilters,
    canManage = true,
    canBlockCollections = true,
    actingTmdbIds = emptySet(),
)

@Composable
private fun Frame(state: BlocklistUiState) {
    BlocklistScreen(
        state = state,
        itemsFor = { flowOf(PagingData.from(emptyList())) },
        events = emptyFlow(),
        shouldRefresh = { _, _ -> false },
        actions =
            BlocklistActions(onBack = {}, onFilterChange = {}, onSearchChange = {}, onOpen = {
                _,
                _,
                ->
            }, onRemove = {}, onRetry = {}),
    )
}
