package com.cooper.binge.companion.seerr.ui.hub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ServerCardChip
import com.binge.designsystem.component.ServerCardStat
import com.binge.designsystem.resolvedContentPadding
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.demo.isDemoOrigin
import com.binge.designsystem.component.ServerCard as DesServerCard

/** The hero: the design system's [ServerCard], fed this server's name, fork, version, address, health and totals. */
@Composable
internal fun ServerCard(
    server: HubServer,
    overview: HubOverview,
    modifier: Modifier = Modifier,
    sides: PaddingValues = resolvedContentPadding(),
) {
    val demo = stringResource(R.string.demo_indicator)
    val updateAvailable = stringResource(R.string.hub_update_available)
    val flags =
        listOfNotNull(
            ServerCardChip(demo, BingeSentiment.Caution).takeIf { server.baseUrl.isDemoOrigin() },
            ServerCardChip(updateAvailable, BingeSentiment.Caution).takeIf { server.updateAvailable },
        )
    DesServerCard(
        title = server.title,
        logo = painterResource(R.drawable.ic_companion),
        modifier = modifier,
        subtitle =
            server.versionLabel
                ?.let { stringResource(R.string.setup_server_edition, server.variant.displayName, it) }
                ?: stringResource(R.string.setup_server_development, server.variant.displayName),
        address = server.baseUrl,
        chips = flags,
        statusChip = ServerCardChip(stringResource(R.string.hub_health_connected), BingeSentiment.Positive),
        stats = serverStats(overview),
        sides = sides,
    )
}

/**
 * The server's totals, in the card's strip.
 *
 * The request figures (movies, TV, pending) are the server's, and every viewer sees them, including one who sees only
 * their own requests (#980). The card is about the server, not about the viewer's list. What the viewer may open is
 * gated where it opens, as the Requests badge is by `canViewRequests`.
 */
@Composable
private fun serverStats(overview: HubOverview): List<ServerCardStat> =
    listOfNotNull(
        ServerCardStat(Icons.Filled.Movie, overview.movieRequestCount?.toString(), stringResource(R.string.hub_quota_movies)),
        ServerCardStat(Icons.Filled.Tv, overview.tvRequestCount?.toString(), stringResource(R.string.hub_quota_tv)),
        // Only a viewer who manages users is told the count, so nobody else gets a stat that never fills in.
        ServerCardStat(Icons.Filled.People, overview.userCount?.toString(), stringResource(R.string.hub_section_users))
            .takeIf { overview.permissions.canManageUsers },
        ServerCardStat(Icons.Filled.HourglassEmpty, overview.pendingRequestCount?.toString(), stringResource(R.string.hub_stat_pending)),
    )

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
