package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.ui.DestinationIcons

/**
 * Who the request is for, offered to a user the server lets make one for someone else. A read still going shows a spinner and a failed
 * one says so, a tap reading it again. The sender is the default, so the row reads "Me" until another user is picked.
 */
@Composable
fun requestForRow(
    state: RequestSheetUiState.Ready,
    actions: RequestSheetActions,
    onOpen: (DestinationField) -> Unit,
): ListItem? =
    when (state.users) {
        UsersState.None -> null
        UsersState.Loading ->
            ListItem(
                DestinationIcons.user,
                label = stringResource(R.string.request_for),
                clickable = false,
                loading = true,
                detailLoading = true,
            )
        UsersState.Failed ->
            failedRow(
                DestinationIcons.user,
                stringResource(R.string.request_sheet_users_failed),
                { actions.onRetryRead(RequestRead.Users) },
            )
        is UsersState.Ready ->
            ListItem(
                DestinationIcons.user,
                label = stringResource(R.string.request_for),
                detail = state.requestFor?.name ?: stringResource(R.string.request_for_me),
                enabled = !state.submitting,
                onClick = { onOpen(DestinationField.RequestFor) },
            )
    }
