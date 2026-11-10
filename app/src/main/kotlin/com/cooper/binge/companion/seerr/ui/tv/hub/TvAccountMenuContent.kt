package com.cooper.binge.companion.seerr.ui.tv.hub

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.nav.TvAccountMenuConfirm
import com.binge.designsystem.tv.nav.TvAccountMenuRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.tv.TvTopBarAccount
import com.cooper.binge.companion.seerr.ui.users.UserDetailUiState
import com.cooper.binge.companion.seerr.ui.users.UserItem
import com.cooper.binge.companion.seerr.ui.state.labelRes as roleLabelRes
import com.cooper.binge.companion.seerr.ui.users.labelRes as originLabelRes

/**
 * The bar's account, assembled for the menu (#1916). The header is [account]'s name with its role, and the origin once the
 * account's own read has it. The rows are Retry when the account could not be read, and Sign out last when [onSignOut] is given
 * (a session sign-in has a session to end; a server's API key has nothing to sign out of), asking first.
 *
 * [whileOpen] is composed with the menu's lines, so only while the menu is open: a place to mark the account's ViewModel visible.
 */
@Composable
internal fun tvAccountMenu(
    account: HubAccount?,
    detail: UserDetailUiState?,
    accountFailed: Boolean,
    onRetry: () -> Unit,
    onSignOut: (() -> Unit)?,
    demo: Boolean,
    whileOpen: @Composable () -> Unit = {},
    initiallyOpen: Boolean = false,
): TvTopBarAccount {
    val separator = stringResource(R.string.hub_meta_separator)
    val origin = accountItem(detail)?.origin
    val roleLine =
        account?.let {
            listOfNotNull(
                stringResource(it.role.roleLabelRes()),
                origin?.let { o ->
                    stringResource(o.originLabelRes())
                },
            ).joinToString(separator)
        }
    val failed = accountFailed || detail is UserDetailUiState.Error
    val retry = stringResource(R.string.hub_retry)
    val signOut = stringResource(R.string.tv_account_sign_out)
    val signOutTitle = stringResource(R.string.tv_account_sign_out_title)
    val signOutMessage = stringResource(R.string.tv_account_sign_out_message)
    val rows =
        buildList {
            if (failed) add(TvAccountMenuRow(key = RETRY_KEY, label = retry, icon = Icons.Filled.Refresh, onClick = onRetry))
            if (onSignOut != null && account != null) {
                add(
                    TvAccountMenuRow(
                        key = SIGN_OUT_KEY,
                        label = signOut,
                        icon = Icons.AutoMirrored.Filled.Logout,
                        onClick = onSignOut,
                        confirm = TvAccountMenuConfirm(title = signOutTitle, message = signOutMessage, confirmLabel = signOut),
                    ),
                )
            }
        }
    return TvTopBarAccount(
        name = account?.name.orEmpty(),
        avatarUrl = account?.avatarUrl,
        demo = demo,
        detail = roleLine,
        rows = rows,
        extraContent = {
            whileOpen()
            AccountMenuLines(detail = detail, accountFailed = accountFailed)
        },
        initiallyOpen = initiallyOpen,
    )
}

private const val RETRY_KEY = "retry"
private const val SIGN_OUT_KEY = "sign-out"

/** The tag on the menu's lines, which a test measures to see that they hold their height while the quota loads. */
internal const val ACCOUNT_MENU_LINES_TAG = "account-menu-lines"

/**
 * What the account menu says under its header (#1916): the email and handle, then each request quota. These are always three
 * lines, a dash standing in for what has not loaded, so the menu does not grow when the numbers arrive. A failed read says so
 * in one line instead, and the menu offers Retry.
 *
 * [detail] is null until the account is known; [accountFailed] says the hub answered without one.
 */
@Composable
internal fun ColumnScope.AccountMenuLines(
    detail: UserDetailUiState?,
    accountFailed: Boolean,
) {
    val failure = (detail as? UserDetailUiState.Error)?.error
    val modifier = Modifier.testTag(ACCOUNT_MENU_LINES_TAG)
    when {
        failure == SeerrError.LocalNetworkDenied -> Line(stringResource(failure.messageRes()), modifier)
        failure != null || accountFailed -> Line(stringResource(R.string.tv_list_load_failed), modifier)
        else -> {
            val item = accountItem(detail)
            val quota = (detail as? UserDetailUiState.Ready)?.detail?.quota
            val separator = stringResource(R.string.hub_meta_separator)
            val placeholder = stringResource(R.string.hub_stat_placeholder)
            Line(item?.let { contactLine(it, separator) } ?: placeholder, modifier)
            Line(stringResource(R.string.hub_quota_movies) + separator + quotaLine(quota?.movie, quota != null))
            Line(stringResource(R.string.hub_quota_tv) + separator + quotaLine(quota?.tv, quota != null))
        }
    }
}

@Composable
private fun Line(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** The signed-in user's row, once the account's own read has it. */
internal fun accountItem(detail: UserDetailUiState?): UserItem? =
    when (detail) {
        is UserDetailUiState.Ready -> detail.detail.item
        is UserDetailUiState.Seeded -> detail.item
        else -> null
    }

/** The email and handle, one when they are the same; a dash when the account has neither, so the line is always there. */
@Composable
private fun contactLine(
    item: UserItem,
    separator: String,
): String {
    val handle = item.handle?.takeUnless { it.equals(item.email, ignoreCase = true) }
    return listOfNotNull(item.email, handle).joinToString(separator).ifEmpty { stringResource(R.string.hub_stat_placeholder) }
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
