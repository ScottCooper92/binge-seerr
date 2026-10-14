package com.cooper.binge.companion.seerr.ui.tv.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvSideSheet
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetStepFocus
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.binge.designsystem.R as DesR
import com.cooper.binge.companion.seerr.ui.state.labelRes as roleLabelRes
import com.cooper.binge.companion.seerr.ui.users.labelRes as originLabelRes

/**
 * Who is signed in, as a side sheet over Home: their name, role and where they come from, email and handle, and what is left of each request quota. Read-outs, so the one row to press is Close; their own requests are a row
 * on Home. Until the account is known it says so, and once the hub has answered without one, or the account's own read
 * failed, it says that with a Retry.
 *
 * [detail] is null until the account is known, and [accountFailed] says it could not be resolved at all.
 */
@Composable
internal fun TvAccountSheet(
    detail: UserDetailUiState?,
    accountFailed: Boolean,
    onRetry: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item =
        when (detail) {
            is UserDetailUiState.Ready -> detail.detail.item
            is UserDetailUiState.Seeded -> detail.item
            else -> null
        }
    val quota = (detail as? UserDetailUiState.Ready)?.detail?.quota
    TvSideSheet(onDismissRequest = onDismissRequest, modifier = modifier) { entryFocus ->
        TvSideSheetTitle(stringResource(R.string.tv_rail_account))
        if (item == null) {
            Unresolved(
                failed = accountFailed || detail is UserDetailUiState.Error,
                denied = (detail as? UserDetailUiState.Error)?.error == SeerrError.LocalNetworkDenied,
                onRetry = onRetry,
                onClose = onDismissRequest,
                entryFocus = entryFocus,
            )
        } else {
            Column(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                Profile(item)
                QuotaRow(stringResource(R.string.hub_quota_movies), quota?.movie, quota != null, Icons.Filled.Movie)
                QuotaRow(stringResource(R.string.hub_quota_tv), quota?.tv, quota != null, Icons.Filled.Tv)
            }
            // Outside the scroll, so the one row to press is always on screen however many read-outs scroll past.
            Close(onDismissRequest, Modifier.focusRequester(entryFocus))
            TvSideSheetStepFocus(entryFocus)
        }
    }
}

@Composable
private fun ColumnScope.Unresolved(
    failed: Boolean,
    denied: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    entryFocus: FocusRequester,
) {
    when {
        denied -> TvSideSheetBody(stringResource(SeerrError.LocalNetworkDenied.messageRes()))
        failed -> TvSideSheetBody(stringResource(R.string.tv_list_load_failed))
        else -> TvSideSheetBody(stringResource(R.string.tv_loading))
    }
    if (failed) {
        TvSideSheetRow(
            label = stringResource(R.string.hub_retry),
            icon = Icons.Filled.Refresh,
            onClick = onRetry,
            modifier = Modifier.focusRequester(entryFocus),
        )
        Close(onClose)
    } else {
        Close(onClose, Modifier.focusRequester(entryFocus))
    }
    TvSideSheetStepFocus(entryFocus)
}

@Composable
private fun Close(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSideSheetRow(label = stringResource(R.string.tv_sheet_close), onClick = onClose, modifier = modifier)
}

/**
 * The signed-in user, in two rows so the page fits the screen with both quotas and Close, since a read-out below the fold
 * is out of D-pad reach: the name with their role and where the account comes from, then their email with their handle.
 */
@Composable
private fun Profile(item: UserItem) {
    val separator = stringResource(R.string.hub_meta_separator)
    TvSideSheetRow(
        label = item.name,
        icon = Icons.Filled.Person,
        enabled = false,
        onClick = {},
        supportingText =
            listOf(stringResource(item.role.roleLabelRes()), stringResource(item.origin.originLabelRes())).joinToString(separator),
    )
    val handle = item.handle?.takeUnless { it.equals(item.email, ignoreCase = true) }
    (item.email ?: handle)?.let { primary ->
        TvSideSheetRow(
            label = primary,
            icon = Icons.Filled.Email,
            enabled = false,
            onClick = {},
            supportingText = handle.takeUnless { it == primary },
        )
    }
}

/** One request type's allowance: what is left of what, with its window, and nothing but placeholders until it has loaded. */
@Composable
private fun QuotaRow(
    label: String,
    bucket: HubQuotaBucket?,
    loaded: Boolean,
    icon: ImageVector,
) {
    TvSideSheetRow(label = label, icon = icon, enabled = false, onClick = {}, supportingText = quotaLine(bucket, loaded))
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
