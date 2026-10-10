package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.AccountProfileCard
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.DetailStatRow
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.resolvedContentPadding
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import io.github.scottcooper92.binge.seerr.ui.state.labelRes
import com.binge.designsystem.R as DesR

/** A dashboard block: a tonal surface with the pane's [sides] around it and the medium spacing inside. */
@Composable
internal fun HubCard(
    modifier: Modifier = Modifier,
    sides: PaddingValues = resolvedContentPadding(),
    contentPadding: PaddingValues = PaddingValues(dimensionResource(DesR.dimen.padding_m)),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(sides)
                .padding(vertical = dimensionResource(DesR.dimen.padding_s)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.hub_card_section_spacing)),
            content = content,
        )
    }
}

/** The hero: the server's name, its fork and version, its address, whether an update is out, and the totals strip. */
@Composable
internal fun ServerCard(
    server: HubServer,
    overview: HubOverview,
    modifier: Modifier = Modifier,
    sides: PaddingValues = resolvedContentPadding(),
) {
    HubCard(modifier, sides) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_companion),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dimensionResource(R.dimen.hub_server_card_logo)),
            )
            Spacer(Modifier.weight(1f))
            if (server.updateAvailable) {
                RequestStateChip(label = stringResource(R.string.hub_update_available), tone = RequestStateTone.Pending)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        server.versionLabel
                            ?.let { stringResource(R.string.setup_server_edition, server.variant.displayName, it) }
                            ?: stringResource(R.string.setup_server_development, server.variant.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = server.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_s)))
            RequestStateChip(label = stringResource(R.string.hub_health_connected), tone = RequestStateTone.Success)
        }
        ServerStatStrip(overview)
    }
}

/**
 * The server's totals as the design system's stat row, the same row Binge's title pages use: an icon,
 * the count, and what it counts. The card already pads its content, so the row adds none of its own.
 *
 * The request figures (movies, TV, pending) are the server's, and every viewer sees them, including one who sees only
 * their own requests (#980). The card is about the server, not about the viewer's list. What the viewer may open is
 * gated where it opens, as the Requests badge is by `canViewRequests`.
 */
@Composable
private fun ServerStatStrip(overview: HubOverview) {
    val placeholder = stringResource(R.string.hub_stat_placeholder)
    DetailStatRow(
        stats =
            listOfNotNull(
                DetailStat(
                    Icons.Filled.Movie,
                    overview.movieRequestCount?.toString() ?: placeholder,
                    stringResource(R.string.hub_quota_movies),
                ),
                DetailStat(Icons.Filled.Tv, overview.tvRequestCount?.toString() ?: placeholder, stringResource(R.string.hub_quota_tv)),
                // Only a viewer who manages users is told the count, so nobody else gets a stat that never fills in.
                DetailStat(Icons.Filled.People, overview.userCount?.toString() ?: placeholder, stringResource(R.string.hub_section_users))
                    .takeIf { overview.permissions.canManageUsers },
                DetailStat(
                    Icons.Filled.HourglassEmpty,
                    overview.pendingRequestCount?.toString() ?: placeholder,
                    stringResource(R.string.hub_stat_pending),
                ),
            ),
        contentPadding = PaddingValues(),
    )
}

/**
 * The connected user: the design system's profile card, with their photo, name and role, and under it their request
 * quota where the server sets one. The whole card opens their page.
 */
@Composable
internal fun AccountCard(
    account: HubAccount,
    quota: HubQuota?,
    modifier: Modifier = Modifier,
    sides: PaddingValues = resolvedContentPadding(),
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(sides).padding(vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        AccountProfileCard(
            name = account.name,
            secondaryLine = stringResource(account.role.labelRes()),
            initialsName = account.name,
            avatarUrl = account.avatarUrl,
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            onClick = onClick,
        )
        quota?.let { QuotaSection(it) }
    }
}

/** Request quota as settings-style rows: Movies and TV, each with what is left, or "Unlimited" and no bar. */
@Composable
internal fun QuotaSection(
    quota: HubQuota,
    modifier: Modifier = Modifier,
) {
    ItemGroup(title = stringResource(R.string.hub_quota_title), rows = quotaRows(quota), modifier = modifier)
}

@Composable
private fun quotaRows(quota: HubQuota): List<ListItem> =
    listOf(
        quotaRow(Icons.Filled.Movie, stringResource(R.string.hub_quota_movies), quota.movie),
        quotaRow(Icons.Filled.Tv, stringResource(R.string.hub_quota_tv), quota.tv),
    )

/** One type's quota: "x of y left" and the window when the server reports one, with a usage bar. A null bucket is unlimited. */
@Composable
private fun quotaRow(
    icon: ImageVector,
    label: String,
    bucket: HubQuotaBucket?,
): ListItem =
    ListItem(
        icon = icon,
        label = label,
        detail =
            if (bucket == null) {
                stringResource(R.string.hub_quota_unlimited)
            } else {
                listOfNotNull(
                    pluralStringResource(R.plurals.hub_quota_remaining, bucket.remaining, bucket.remaining, bucket.limit),
                    bucket.days?.let { pluralStringResource(R.plurals.hub_quota_period, it, it) },
                ).joinToString(stringResource(R.string.hub_meta_separator))
            },
        clickable = false,
        trailingContent =
            bucket?.let {
                {
                    LinearProgressIndicator(
                        progress = { it.used.toFloat() / it.limit },
                        modifier = Modifier.width(dimensionResource(R.dimen.hub_quota_bar_width)),
                    )
                }
            },
    )
