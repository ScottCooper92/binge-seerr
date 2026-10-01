package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.SettingsGroup
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.component.SettingsRows
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.accent
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import com.binge.designsystem.R as DesR

/** A dashboard block: a tonal surface with the screen inset around it and the medium spacing inside. */
@Composable
internal fun HubCard(
    modifier: Modifier = Modifier,
    inset: Dp = resolvedContentInset(),
    contentPadding: PaddingValues = PaddingValues(dimensionResource(DesR.dimen.padding_m)),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = inset,
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
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
    inset: Dp = resolvedContentInset(),
) {
    HubCard(modifier, inset) {
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

@Composable
private fun ServerStatStrip(overview: HubOverview) {
    val placeholder = stringResource(R.string.hub_stat_placeholder)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ServerStat(overview.movieRequestCount?.toString() ?: placeholder, stringResource(R.string.hub_quota_movies), Modifier.weight(1f))
        StatDivider()
        ServerStat(overview.tvRequestCount?.toString() ?: placeholder, stringResource(R.string.hub_quota_tv), Modifier.weight(1f))
        StatDivider()
        ServerStat(overview.userCount?.toString() ?: placeholder, stringResource(R.string.hub_section_users), Modifier.weight(1f))
        StatDivider()
        ServerStat(overview.pendingRequestCount?.toString() ?: placeholder, stringResource(R.string.hub_stat_pending), Modifier.weight(1f))
    }
}

@Composable
private fun ServerStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier =
            Modifier
                .width(dimensionResource(DesR.dimen.hairline_thickness))
                .height(dimensionResource(R.dimen.hub_stat_divider_height))
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** The connected user: their name, their role, and their request quota where the server sets one. */
@Composable
internal fun AccountCard(
    account: HubAccount,
    quota: HubQuota?,
    modifier: Modifier = Modifier,
    inset: Dp = resolvedContentInset(),
    onClick: (() -> Unit)? = null,
) {
    // The card carries no padding of its own: the name row and the quota rows each bring their row padding, so none is doubled.
    HubCard(if (onClick != null) modifier.clickable(onClick = onClick) else modifier, inset, contentPadding = PaddingValues()) {
        val rowPadding = dimensionResource(DesR.dimen.settings_group_row_padding_h)
        Row(
            modifier =
                Modifier.padding(
                    start = rowPadding,
                    top = rowPadding,
                    end = rowPadding,
                    bottom =
                        if (quota ==
                            null
                        ) {
                            rowPadding
                        } else {
                            Dp.Hairline
                        },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_s)))
            val sentiment = if (account.isAdmin) BingeSentiment.Info else BingeSentiment.Neutral
            BingeTag(
                label = stringResource(if (account.isAdmin) R.string.hub_role_admin else R.string.hub_role_user),
                tint = sentiment.accent(),
                fill = sentiment.fill(),
            )
        }
        quota?.let {
            Column {
                Text(
                    text = stringResource(R.string.hub_quota_title).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = rowPadding).semantics { heading() },
                )
                SettingsRows(quotaRows(it))
            }
        }
    }
}

/** Request quota as settings-style rows: Movies and TV, each with what is left, or "Unlimited" and no bar. */
@Composable
internal fun QuotaSection(
    quota: HubQuota,
    modifier: Modifier = Modifier,
) {
    SettingsGroup(title = stringResource(R.string.hub_quota_title), rows = quotaRows(quota), modifier = modifier)
}

@Composable
private fun quotaRows(quota: HubQuota): List<SettingsRow> =
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
): SettingsRow =
    SettingsRow(
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
