package com.cooper.binge.companion.seerr.ui.tv.hub

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvInfoCard
import com.binge.designsystem.tv.component.TvSideSheet
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetStepFocus
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.hub.HubQuota
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.state.labelRes as roleLabelRes
import com.cooper.binge.companion.seerr.ui.users.labelRes as originLabelRes

/**
 * Who is signed in, as a side sheet over Home: one card with their name, role and where they come from, email and handle,
 * and what is left of each request quota. Back is the way out, so there is no Close. Until the account is known it says
 * so, and once the hub has answered without one, or the account's own read failed, it says that with a Retry.
 *
 * [detail] is null until the account is known, and [accountFailed] says it could not be resolved at all. [onSignOut] ends
 * the session and is null for a connection with none to end, a server's API key; it is the last row, and asks first.
 */
@Composable
internal fun TvAccountSheet(
    detail: UserDetailUiState?,
    accountFailed: Boolean,
    onRetry: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onSignOut: (() -> Unit)? = null,
) {
    val item =
        when (detail) {
            is UserDetailUiState.Ready -> detail.detail.item
            is UserDetailUiState.Seeded -> detail.item
            else -> null
        }
    val quota = (detail as? UserDetailUiState.Ready)?.detail?.quota
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    if (confirmingSignOut && onSignOut != null) {
        TvConfirmDialog(
            title = stringResource(R.string.tv_account_sign_out_title),
            message = stringResource(R.string.tv_account_sign_out_message),
            confirmLabel = stringResource(R.string.tv_account_sign_out),
            onConfirm = onSignOut,
            onDismissRequest = { confirmingSignOut = false },
            destructive = true,
        )
    }
    TvSideSheet(onDismissRequest = onDismissRequest, modifier = modifier) { entryFocus ->
        TvSideSheetTitle(stringResource(R.string.tv_rail_account))
        if (item == null) {
            Unresolved(
                failed = accountFailed || detail is UserDetailUiState.Error,
                denied = (detail as? UserDetailUiState.Error)?.error == SeerrError.LocalNetworkDenied,
                onRetry = onRetry,
                entryFocus = entryFocus,
            )
        } else {
            TvSideSheetRows {
                TvInfoCard(
                    title = item.name,
                    lines = accountLines(item, quota),
                    icon = Icons.Filled.Person,
                    modifier = Modifier.focusRequester(entryFocus),
                )
                onSignOut?.let {
                    TvSideSheetRow(
                        label = stringResource(R.string.tv_account_sign_out),
                        icon = Icons.AutoMirrored.Filled.Logout,
                        destructive = true,
                        onClick = { confirmingSignOut = true },
                    )
                }
            }
            TvSideSheetStepFocus(entryFocus)
        }
    }
}

@Composable
private fun ColumnScope.Unresolved(
    failed: Boolean,
    denied: Boolean,
    onRetry: () -> Unit,
    entryFocus: FocusRequester,
) {
    // With nothing to press, focus waits on the message itself, so it neither falls to the page beneath nor lands on
    // silence: a screen reader reads the message out.
    val holdsFocus = if (failed) Modifier else Modifier.focusRequester(entryFocus).focusable()
    when {
        denied -> TvSideSheetBody(stringResource(SeerrError.LocalNetworkDenied.messageRes()), modifier = holdsFocus)
        failed -> TvSideSheetBody(stringResource(R.string.tv_list_load_failed))
        else -> TvSideSheetBody(stringResource(R.string.tv_loading), modifier = holdsFocus)
    }
    if (failed) {
        TvSideSheetRows {
            TvSideSheetRow(
                label = stringResource(R.string.hub_retry),
                icon = Icons.Filled.Refresh,
                onClick = onRetry,
                modifier = Modifier.focusRequester(entryFocus),
            )
        }
    }
    TvSideSheetStepFocus(entryFocus)
}

/**
 * The account card's lines: role and where the account comes from, then email and handle (one when they are the same),
 * then each request quota. The email line goes when the account has neither, so the card reads in three or four lines.
 */
@Composable
private fun accountLines(
    item: UserItem,
    quota: HubQuota?,
): List<String> {
    val separator = stringResource(R.string.hub_meta_separator)
    val handle = item.handle?.takeUnless { it.equals(item.email, ignoreCase = true) }
    return listOfNotNull(
        listOf(stringResource(item.role.roleLabelRes()), stringResource(item.origin.originLabelRes())).joinToString(separator),
        listOfNotNull(item.email, handle).joinToString(separator).ifEmpty { null },
        stringResource(R.string.hub_quota_movies) + separator + quotaLine(quota?.movie, quota != null),
        stringResource(R.string.hub_quota_tv) + separator + quotaLine(quota?.tv, quota != null),
    )
}

/**
 * What a quota says: a placeholder until it has loaded, Unlimited for a bucket with no limit, None left once spent, else how
 * many are left of the limit, and the window it renews over.
 */
@Composable
internal fun quotaLine(
    bucket: HubQuotaBucket?,
    loaded: Boolean,
): String {
    if (!loaded) return stringResource(R.string.hub_stat_placeholder)
    if (bucket == null) return stringResource(R.string.hub_quota_unlimited)
    val remaining =
        if (bucket.remaining <= 0) {
            stringResource(R.string.tv_hub_quota_none_left)
        } else {
            pluralStringResource(R.plurals.hub_quota_remaining, bucket.remaining, bucket.remaining, bucket.limit)
        }
    val window = bucket.days?.let { pluralStringResource(R.plurals.hub_quota_period, it, it) }
    return listOfNotNull(remaining, window).joinToString(stringResource(R.string.hub_meta_separator))
}
