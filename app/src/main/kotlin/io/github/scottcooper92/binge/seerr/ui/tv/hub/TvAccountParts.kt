package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.BingeTvInitialsAvatar
import com.binge.designsystem.tv.focus.tvFocusIndicator
import com.binge.designsystem.tv.focus.tvFocusTarget
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuotaBucket
import io.github.scottcooper92.binge.seerr.ui.users.UserItem
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR
import io.github.scottcooper92.binge.seerr.ui.state.labelRes as roleLabelRes
import io.github.scottcooper92.binge.seerr.ui.users.labelRes as originLabelRes

/** The signed-in user as the phone's page shows them. Takes focus, so the rail does not hold it over a page with nothing else above the row. */
@Composable
internal fun TvProfileCard(
    item: UserItem,
    now: Long,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = BingeShapes.AccountCard
    val separator = stringResource(R.string.hub_meta_separator)
    val username = item.handle ?: item.email
    val joined = formatRelativeOrAbsolute(item.createdAtMillis, now)?.let { stringResource(R.string.user_joined, it) }
    Row(
        modifier =
            modifier
                .tvFocusIndicator(isFocused = focused, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(dimensionResource(TvR.dimen.tv_button_border_width), MaterialTheme.colorScheme.border, shape)
                .tvFocusTarget {
                    focused = it
                    if (it) onFocused()
                }.semantics(mergeDescendants = true) {}
                .padding(dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BingeTvInitialsAvatar(name = item.name, size = dimensionResource(R.dimen.tv_account_avatar_size), avatarUrl = item.avatarUrl)
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xxs))) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(
                listOfNotNull(username, joined).joinToString(separator).ifEmpty { null },
                item.email?.takeUnless { it.equals(username, ignoreCase = true) },
            ).forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text =
                    listOf(
                        stringResource(item.role.roleLabelRes()),
                        stringResource(item.origin.originLabelRes()),
                    ).joinToString(separator),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

/**
 * One request type's allowance as a tile: what is left in large type, what it is out of, the window, and a bar of
 * what is used. A null bucket is unlimited and has no bar; a quota that has not loaded yet shows placeholders.
 */
@Composable
internal fun TvQuotaTile(
    label: String,
    bucket: HubQuotaBucket?,
    loaded: Boolean,
    modifier: Modifier = Modifier,
) {
    val exhausted = bucket != null && bucket.remaining <= 0
    val shape = BingeShapes.AccountCard
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(dimensionResource(TvR.dimen.tv_button_border_width), MaterialTheme.colorScheme.border, shape)
                .padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xxs)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        when {
            !loaded ->
                Text(
                    text = stringResource(R.string.hub_stat_placeholder),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            bucket == null -> {
                Text(
                    text = stringResource(R.string.hub_quota_unlimited),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.tv_account_quota_unlimited_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Text(
                    text = bucket.remaining.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text =
                        if (exhausted) {
                            stringResource(
                                R.string.tv_hub_quota_none_left,
                            )
                        } else {
                            stringResource(R.string.tv_account_quota_of, bucket.limit)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                bucket.days?.let {
                    Text(
                        text = pluralStringResource(R.plurals.hub_quota_period, it, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (loaded && bucket != null) {
            val fraction = if (bucket.limit > 0) bucket.used.toFloat() / bucket.limit else 0f
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(dimensionResource(R.dimen.tv_download_progress_height))
                        .clip(BingeShapes.Pill)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(dimensionResource(R.dimen.tv_download_progress_height))
                            .background(if (exhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
