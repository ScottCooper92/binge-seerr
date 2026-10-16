package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubViewModel
import com.cooper.binge.companion.seerr.ui.tv.hub.TvAccountSheet
import com.cooper.binge.companion.seerr.ui.tv.requests.TvMyRequests
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsGridContent
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserDetailViewModel

/**
 * Who is signed in: the sheet reads the same hub state Home's top row does, and the account's own detail once it has an id.
 * [onSignOut] is null for a connection with no session to end.
 */
@Composable
internal fun TvAccountEntry(
    hubViewModel: HubViewModel,
    onDismissRequest: () -> Unit,
    onSignOut: (() -> Unit)? = null,
) {
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    // The hub is `Lazily` and only auto-retries while it is visible, so this sheet says so, as Home and Settings do.
    DisposableEffect(hubViewModel) {
        // It reads the account and nothing of the dashboard's, so no downloads poll and no counts (#827).
        hubViewModel.setScreenVisible(true, dashboard = false)
        onDispose { hubViewModel.setScreenVisible(false) }
    }
    val account = (hub as? HubUiState.Ready)?.overview?.account
    if (account == null) {
        // Still loading while the hub is, failed once it has answered without an account.
        TvAccountSheet(
            detail = null,
            accountFailed = hub !is HubUiState.Loading,
            onRetry = hubViewModel::recheck,
            onDismissRequest = onDismissRequest,
        )
        return
    }
    TvAccountContent(accountId = account.id, onDismissRequest = onDismissRequest, onSignOut = onSignOut)
}

/** The account's own sheet: the same [UserDetailViewModel] as the phone's user page, bound to the signed-in user. */
@Composable
private fun TvAccountContent(
    accountId: Int,
    onDismissRequest: () -> Unit,
    onSignOut: (() -> Unit)?,
    viewModel: UserDetailViewModel = accountViewModel(accountId),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    TvAccountSheet(
        detail = state,
        accountFailed = false,
        onRetry = viewModel::reload,
        onDismissRequest = onDismissRequest,
        onSignOut = onSignOut,
    )
}

/**
 * The signed-in user's own requests as a Home row, on the one [UserDetailViewModel] the account sheet and the see-all grid share.
 * Null while the account is unknown, which leaves Home to the server's filters.
 */
@Composable
internal fun rememberMyRequests(
    accountId: Int?,
    onSeeAll: () -> Unit,
): TvMyRequests? {
    accountId ?: return null
    val viewModel = accountViewModel(accountId)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requests = viewModel.requests.collectAsLazyPagingItems()
    // The list is a plain pager with no cache behind it, so a return re-reads it here. The first resume finds it still
    // on its first load and leaves it be. The ViewModel's visibility belongs to the account page that shares it.
    LifecycleResumeEffect(requests) {
        if (requests.loadState.refresh !is LoadState.Loading) requests.refresh()
        onPauseOrDispose {}
    }
    val count =
        when (val ready = state) {
            is UserDetailUiState.Ready -> ready.detail.item.requestCount
            is UserDetailUiState.Seeded -> ready.item.requestCount
            else -> null
        }
    return TvMyRequests(rows = requests.toRows(null) { it.id }, count = count, onSeeAll = onSeeAll, onRetry = requests::retry)
}

/** The signed-in user's [UserDetailViewModel], under one key so the page and its see-all grid share the one paged list. */
@Composable
private fun accountViewModel(accountId: Int): UserDetailViewModel =
    hiltViewModel<UserDetailViewModel, UserDetailViewModel.Factory>(
        key = "account-$accountId",
        creationCallback = { factory -> factory.create(accountId) },
    )

/** Every request the signed-in user has made, as a paged grid above Home, on the same ViewModel as the account page. */
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
        detailOpen = detailOpen,
        onOpenDetail = { onOpenRequest(it.id) },
        onRetryLoad = { requests.retry() },
        onBack = onDone,
        now = (state as? UserDetailUiState.Ready)?.now ?: System.currentTimeMillis(),
    )
}
