package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.SearchCallbacks
import com.binge.designsystem.template.SearchScreen
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.ui.hub.AccountBar
import com.cooper.binge.companion.seerr.ui.hub.TitleBarAccount
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen

/** What the screen does with what the user does; the entry wires each to the view model. */
internal class TitleSearchActions(
    val onQueryChange: (String) -> Unit,
    val onClear: () -> Unit,
    val onSubmit: () -> Unit,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onRecent: (String) -> Unit,
    val onForgetRecents: () -> Unit,
    val onOpenTitle: (TitleResult) -> Unit,
    val onType: (SearchType) -> Unit,
    val onOpenGrid: (HubGrid, String) -> Unit,
    val onOpenStudio: (StudioResult) -> Unit,
)

/**
 * The Search tab, drawn by the design system's [SearchScreen]: the title gives way as the results scroll, and the field and the type
 * chips stay pinned under it. The account sits beside the title and in the collapsed bar, as on the Discover grid. Below the bar is
 * whichever of the idle offer, results or a message the state calls for. What is Seerr's own is mapped in `TitleSearchMapping.kt`.
 */
@Composable
internal fun TitleSearchScreen(
    state: TitleSearchUiState,
    actions: TitleSearchActions,
    modifier: Modifier = Modifier,
    accountBar: AccountBar? = null,
    onOpenAccount: (Int) -> Unit = {},
) {
    val titles = (state as? TitleSearchUiState.Ready)?.results.orEmpty().associateBy { it.resultKey() }
    val studios = (state as? TitleSearchUiState.Ready)?.studios.orEmpty().associateBy { it.resultKey() }
    val error = (state as? TitleSearchUiState.Error)?.error
    SearchScreen(
        title = stringResource(R.string.title_search_title),
        text = state.text,
        state = state.toSearchState(),
        callbacks =
            SearchCallbacks(
                onQueryChange = actions.onQueryChange,
                onClear = actions.onClear,
                onOpenResult = { key ->
                    titles[key]?.let(actions.onOpenTitle) ?: studios[key]?.let(actions.onOpenStudio)
                },
                onLoadMore = actions.onLoadMore,
                onRetry = actions.onRetry,
                onSubmit = actions.onSubmit,
                onType = { key -> actions.onType(SearchType.valueOf(key)) },
                onPickRecent = actions.onRecent,
                onClearRecents = actions.onForgetRecents,
            ),
        placeholder = stringResource(R.string.title_search_placeholder),
        modifier = modifier,
        idleTitle = stringResource(R.string.title_search_idle_title),
        idleMessage = stringResource(R.string.title_search_idle_message),
        reselectKey = SeerrTab.Search,
        recentsTitle = stringResource(R.string.title_search_recent),
        clearLabel = stringResource(R.string.title_search_recent_clear),
        clearTitle = stringResource(R.string.title_search_recent_clear_title),
        clearMessage = stringResource(R.string.title_search_recent_clear_message),
        moreFailedLabel = stringResource(R.string.title_search_more_failed),
        titleTrailing = accountBar?.let { bar -> { interactive -> TitleBarAccount(bar, interactive, onOpenAccount) } },
        barActions = { handedOver, fraction -> accountBar?.let { TitleBarAccount(it, handedOver, onOpenAccount, fraction) } },
        resultTrailing = { media -> titles[media.key]?.let { result -> { ResultStatus(result) } } },
        idleExtras = (state as? TitleSearchUiState.Idle)?.let { idle -> { trendingRails(idle.trending, actions.onOpenTitle) } },
        noResultsExtras =
            (state as? TitleSearchUiState.NoResults)?.takeIf { it.browseAll }?.let { none ->
                { BrowseAllRow(none, actions.onOpenGrid) }
            },
        failedContent = error?.let { failure -> { modifier -> ErrorScreen(failure, modifier, onRetry = actions.onRetry) } },
    )
}
