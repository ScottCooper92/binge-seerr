package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvAccountBoard
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsGridContent
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailUiState
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailViewModel

/** Who is signed in, and what they have requested: the page reads the same hub state the rail's avatar does. */
@Composable
internal fun TvAccountEntry(
    openRequestId: Int?,
    onOpenRequest: (Int) -> Unit,
    seeAllOpen: Boolean,
    onSeeAll: () -> Unit,
    hubViewModel: HubViewModel,
) {
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    // The hub is `Lazily` and only auto-retries while it is visible, so this page says so, as Home and Settings do.
    DisposableEffect(hubViewModel) {
        // It reads the account and nothing of the dashboard's, so no downloads poll and no counts (#827).
        hubViewModel.setScreenVisible(true, dashboard = false)
        onDispose { hubViewModel.setScreenVisible(false) }
    }
    val account = (hub as? HubUiState.Ready)?.overview?.account
    if (account == null) {
        // Still loading while the hub is, failed once it has answered without an account.
        TvAccountBoard(
            detail = null,
            requests = TvPagedRows(count = 0, at = { null }),
            onOpenRequest = {},
            onRetry = hubViewModel::recheck,
            onRetryRequests = {},
            overlayOpen = false,
            accountFailed = hub !is HubUiState.Loading,
        )
        return
    }
    TvAccountContent(
        accountId = account.id,
        overlayOpen = openRequestId != null || seeAllOpen,
        onOpenRequest = onOpenRequest,
        onSeeAll = onSeeAll,
    )
}

/** The account's own page: the same [UserDetailViewModel] as the phone's user page, bound to the signed-in user. */
@Composable
private fun TvAccountContent(
    accountId: Int,
    overlayOpen: Boolean,
    onOpenRequest: (Int) -> Unit,
    onSeeAll: () -> Unit,
    viewModel: UserDetailViewModel = accountViewModel(accountId),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requests = viewModel.requests.collectAsLazyPagingItems()
    TvAccountBoard(
        detail = state,
        requests = requests.toRows(null) { it.id },
        onOpenRequest = { onOpenRequest(it.id) },
        onRetry = viewModel::reload,
        onRetryRequests = requests::retry,
        overlayOpen = overlayOpen,
        onSeeAll = onSeeAll,
    )
}

/** The signed-in user's [UserDetailViewModel], under one key so the page and its see-all grid share the one paged list. */
@Composable
private fun accountViewModel(accountId: Int): UserDetailViewModel =
    hiltViewModel<UserDetailViewModel, UserDetailViewModel.Factory>(
        key = "account-$accountId",
        creationCallback = { factory -> factory.create(accountId) },
    )

/** Every request the signed-in user has made, as a paged grid above the rail, on the same ViewModel as the account page. */
@Composable
internal fun TvAccountRequestsGridOverlay(
    accountId: Int,
    detailOpen: Boolean,
    onOpenRequest: (Int) -> Unit,
    onDone: () -> Unit,
    viewModel: UserDetailViewModel = accountViewModel(accountId),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requests = viewModel.requests.collectAsLazyPagingItems()
    val count =
        when (val ready = state) {
            is UserDetailUiState.Ready -> ready.detail.item.requestCount
            is UserDetailUiState.Seeded -> ready.item.requestCount
            else -> null
        }
    val heading = stringResource(R.string.tv_account_your_requests)
    TvRequestsGridContent(
        heading = if (count != null) stringResource(R.string.tv_filter_with_count, heading, count) else heading,
        emptyBody = stringResource(R.string.tv_account_requests_empty),
        rows = requests.toRows(null) { it.id },
        actingIds = emptySet(),
        detailOpen = detailOpen,
        onOpenDetail = { onOpenRequest(it.id) },
        onRetryLoad = { requests.retry() },
        onBack = onDone,
    )
}
