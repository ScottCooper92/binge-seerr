package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.runtime.Composable
import androidx.paging.compose.LazyPagingItems
import com.binge.designsystem.template.PagedPhase
import com.binge.designsystem.template.PagedRefresh
import io.github.scottcooper92.binge.seerr.data.ListRefresh
import com.binge.designsystem.template.rememberPagedPhase as rememberDesignSystemPagedPhase

/**
 * The phase of the list on screen, by the design system's [PagedPhase] rules, which were lifted from this
 * app. [lastRefresh] is the latest finished refresh of this list from its view model; a list without a
 * mediator passes null.
 */
@Composable
internal fun LazyPagingItems<*>.rememberPagedPhase(lastRefresh: ListRefresh?): PagedPhase =
    rememberDesignSystemPagedPhase(key = this, loadState = loadState, itemCount = itemCount, lastRefresh = lastRefresh?.toPagedRefresh())

/** This app's record of a refresh, as the design system's phase reads it. */
internal fun ListRefresh.toPagedRefresh(): PagedRefresh = PagedRefresh(rowsWritten = rowsWritten, sequence = sequence)
