package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import com.binge.designsystem.R as DesR

/** Under this much of the tighter quota used, the ring is green. */
private const val QUOTA_CAUTION_FROM = 0.5f

/** From this much used, the ring is red: little is left. */
private const val QUOTA_NEGATIVE_FROM = 0.8f

/**
 * The connected user as the hub bar's trailing action (#1325): their photo, or their initials, inside a ring that fills
 * as the tighter of their movie and TV quotas is used. Green, then yellow from half used, then red from four fifths. An
 * unlimited account's ring is full and green. With no quota read, there is no ring. It opens the user's own page, which
 * holds the full quota.
 */
@Composable
internal fun HubAccountAction(
    account: HubAccount,
    quota: HubQuota?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = accountDescription(account, quota)
    Box(
        modifier =
            modifier
                .size(dimensionResource(DesR.dimen.min_touch_target))
                .clip(BingeShapes.Pill)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // One announcement for the whole action: the avatar's initials are not read on their own.
        Box(modifier = Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            quota?.let { QuotaRing(it) }
            BingeInitialsAvatar(
                name = account.name,
                avatarUrl = account.avatarUrl,
                size = dimensionResource(R.dimen.hub_account_avatar_size),
            )
        }
    }
}

@Composable
private fun QuotaRing(quota: HubQuota) {
    val used = quota.tightest()?.bucket?.usedFraction
    CircularProgressIndicator(
        // Unlimited is the full ring.
        progress = { used ?: 1f },
        modifier = Modifier.size(dimensionResource(R.dimen.hub_account_ring_size)),
        color = quotaSentiment(used ?: 0f).fill(),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeWidth = dimensionResource(R.dimen.hub_account_ring_stroke),
    )
}

/** The ring's colour for [used], a fraction of the tighter quota: plenty left, getting low, or nearly gone. */
internal fun quotaSentiment(used: Float): BingeSentiment =
    when {
        used < QUOTA_CAUTION_FROM -> BingeSentiment.Positive
        used < QUOTA_NEGATIVE_FROM -> BingeSentiment.Caution
        else -> BingeSentiment.Negative
    }

/** The user's name and what the ring shows: what is left of the tighter quota, or that there is no limit. */
@Composable
private fun accountDescription(
    account: HubAccount,
    quota: HubQuota?,
): String {
    if (quota == null) return account.name
    val tightest = quota.tightest() ?: return stringResource(R.string.hub_account_unlimited, account.name)
    val bucket = tightest.bucket
    val plural = if (tightest.type == HubQuotaType.Movie) R.plurals.hub_account_movies_left else R.plurals.hub_account_tv_left
    return pluralStringResource(plural, bucket.limit, account.name, bucket.remaining, bucket.limit)
}
