package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.template.PagedPhase

/**
 * A paged list under the design system's [BingePullToRefresh]. A pull is the paging refresh, [onRefresh], which
 * re-runs the list's remote mediator where it has one.
 *
 * The spinner is the list's own refresh behind rows on screen, so a list under this draws no refresh bar of its own.
 * A pull while any refresh is in flight is dropped: under a skeleton the first load is still running, and a second
 * would only restart it.
 *
 * [contentPadding] is the page's. Its top is the bar and any header over the rows, and the spinner rests below it.
 * [state] is for a still frame, which passes one resting at the threshold; null remembers M3's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PagedPullToRefresh(
    phase: PagedPhase,
    loadState: CombinedLoadStates,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    state: PullToRefreshState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val inFlight = loadState.refreshInFlight()
    BingePullToRefresh(
        isRefreshing = phase is PagedPhase.Rows && phase.refreshing,
        onRefresh = { if (!inFlight) onRefresh() },
        modifier = modifier,
        indicatorTopInset = contentPadding.calculateTopPadding(),
        state = state ?: rememberPullToRefreshState(),
        content = content,
    )
}

/** Whether a refresh of the list is running, from the cache or from the server. */
internal fun CombinedLoadStates.refreshInFlight(): Boolean = source.refresh is LoadState.Loading || mediator?.refresh is LoadState.Loading

/**
 * A whole-page message a pull can reach. M3's pull reads a scroll, so the message sits in one that fills the page
 * and goes nowhere. [content] is handed the page's height, so it centres as it would unwrapped.
 */
@Composable
internal fun PullableMessage(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = maxHeight
        Box(Modifier.verticalScroll(rememberScrollState())) { content(Modifier.height(height)) }
    }
}
