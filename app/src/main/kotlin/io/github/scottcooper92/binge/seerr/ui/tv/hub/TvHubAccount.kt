package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.HubAccount
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuota
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuotaBucket

/**
 * The signed-in account and what is left of its request quota.
 *
 * A read-out, never a focus stop: the person on a television is usually a requester, and the
 * question they have before asking Binge for another title is how many they have left. The user
 * page itself stays on the phone, so there is nothing here to open.
 */
@Composable
internal fun TvHubAccount(
    account: HubAccount,
    quota: HubQuota?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.hub_quota_meter_spacing)),
    ) {
        Text(
            text =
                listOf(
                    account.name,
                    stringResource(if (account.isAdmin) R.string.hub_role_admin else R.string.hub_role_user),
                ).joinToString(stringResource(R.string.hub_meta_separator)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        quota?.let { TvQuotaMeters(it) }
    }
}

/**
 * Both meters side by side.
 *
 * The title earns its place here in a way it does not on the phone: the stat tiles below already
 * carry "Movies" and "TV" for the request *counts*, so unlabelled meters would put the same two
 * words twice on one board meaning two different things.
 */
@Composable
private fun TvQuotaMeters(quota: HubQuota) {
    Text(
        text = stringResource(R.string.hub_quota_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_stat_tile_gap))) {
        // Sized, not weighted: split across the board a label sits the better part of 450dp from its
        // own number, which at ten feet stops reading as one fact and starts reading as two.
        val width = Modifier.width(dimensionResource(R.dimen.tv_quota_meter_width))
        TvQuotaMeter(stringResource(R.string.hub_quota_movies), quota.movie, width)
        TvQuotaMeter(stringResource(R.string.hub_quota_tv), quota.tv, width)
    }
}

/**
 * One type's allowance: what is left, over a bar of what is used. A null bucket is unlimited.
 *
 * An exhausted bucket is called out rather than left to read as "0 of 10": at zero the quota has
 * stopped being a number and become the reason the next request would be refused.
 */
@Composable
private fun TvQuotaMeter(
    label: String,
    bucket: HubQuotaBucket?,
    modifier: Modifier = Modifier,
) {
    val exhausted = bucket != null && bucket.remaining <= 0
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.hub_quota_meter_line_spacing)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // The window rides on the label rather than taking a line of its own: the board does not
                // scroll, and at 540dp a line per meter is what pushes the hint off the bottom.
                text =
                    listOfNotNull(
                        label,
                        bucket?.days?.let { pluralStringResource(R.plurals.hub_quota_period, it, it) },
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    when {
                        bucket == null -> stringResource(R.string.hub_quota_unlimited)
                        exhausted -> stringResource(R.string.tv_hub_quota_none_left)
                        else ->
                            pluralStringResource(
                                R.plurals.hub_quota_remaining,
                                bucket.remaining,
                                bucket.remaining,
                                bucket.limit,
                            )
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        QuotaBar(bucket = bucket, exhausted = exhausted)
    }
}

/**
 * The used portion over the whole allowance. An unlimited bucket draws the bare track: there is no
 * proportion to show, and a filled bar would read as a quota that is entirely spent.
 */
@Composable
private fun QuotaBar(
    bucket: HubQuotaBucket?,
    exhausted: Boolean,
) {
    val height = dimensionResource(R.dimen.tv_download_progress_height)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(BingeShapes.Pill)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bucket != null) {
            // A limit of 0 is a quota that allows nothing, and would divide to NaN rather than to "full".
            val used = if (bucket.limit > 0) (bucket.used.toFloat() / bucket.limit).coerceIn(0f, 1f) else 1f
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(used)
                        .height(height)
                        .background(
                            if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        ),
            )
        }
    }
}
