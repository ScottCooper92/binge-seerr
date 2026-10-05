package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusIndicator
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AllowLocalNetwork
import io.github.scottcooper92.binge.seerr.ui.hub.BingeStatus
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubDownload
import io.github.scottcooper92.binge.seerr.ui.hub.HubServer
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.isProblem
import io.github.scottcooper92.binge.seerr.ui.rememberAllowLocalNetwork
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardFrame
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardPlate
import io.github.scottcooper92.binge.seerr.ui.tv.TvPoster
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

private const val DOWNLOAD_STRIP_MAX = 4
private const val PERCENT = 100

/** The stat tiles a preview can seed as focused. */
internal const val TILE_PENDING = "pending"
internal const val TILE_ISSUES = "issues"

/** Everything the hub board can ask for, in one place so the entry stays a wiring. */
internal class TvHubActions(
    val onOpenRequests: () -> Unit,
    val onOpenIssues: () -> Unit,
    val onRetry: () -> Unit,
    val onReconnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onOpenBingeListing: () -> Unit,
)

/**
 * The hub as a television board: the server's name on the title line, its health headline, the counts as
 * tiles — the pending requests and the open issues open their lists — and what is downloading now. When
 * the server cannot be reached, the problem and its way out take the board instead.
 */
@Composable
internal fun TvHubBoard(
    state: HubUiState,
    actions: TvHubActions,
    modifier: Modifier = Modifier,
    initialFocusedTile: String? = null,
    initialBingeTileFocused: Boolean = false,
) {
    val ready = state as? HubUiState.Ready
    // Held here, above the health branches: a re-probe passes through Checking and would drop it from the problem panel.
    val health = if (state is HubUiState.Error) state.health else ready?.health
    val allow = rememberAllowLocalNetwork { if (health == ConnectionHealth.LocalNetworkDenied) actions.onRetry() }
    TvBoardFrame(title = ready?.server?.title ?: stringResource(R.string.companion_name), modifier = modifier) {
        when {
            state is HubUiState.Error -> TvHubProblem(state.health, actions, allow, modifier = Modifier.weight(1f))
            ready == null -> TvBoardPlate(body = stringResource(R.string.tv_loading), modifier = Modifier.weight(1f))
            ready.health.isProblem() -> TvHubProblem(ready.health, actions, allow, modifier = Modifier.weight(1f))
            else -> TvHubDashboard(ready, actions, initialFocusedTile, initialBingeTileFocused)
        }
    }
}

/** Server gone or the dashboard not loaded (retry, or edit the connection), or the session rejected (reconnect). */
@Composable
private fun TvHubProblem(
    health: ConnectionHealth,
    actions: TvHubActions,
    allow: AllowLocalNetwork,
    modifier: Modifier = Modifier,
) {
    val unauthorized = health == ConnectionHealth.Unauthorized
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvBoardPlate(
        headline =
            stringResource(
                when (health) {
                    ConnectionHealth.Unauthorized -> R.string.hub_unauthorized_headline
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_headline
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_headline
                    else -> R.string.hub_unreachable_headline
                },
            ),
        body =
            stringResource(
                when (health) {
                    ConnectionHealth.Unauthorized -> R.string.hub_unauthorized_body
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_body
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_body
                    else -> R.string.hub_unreachable_body
                },
            ),
        icon = Icons.Filled.Warning,
        primary =
            if (unauthorized) {
                stringResource(R.string.tv_hub_reconnect) to actions.onReconnect
            } else if (health == ConnectionHealth.LocalNetworkDenied) {
                stringResource(allow.shortLabel) to allow.run
            } else {
                stringResource(R.string.hub_retry) to actions.onRetry
            },
        alternate = if (unauthorized) null else stringResource(R.string.settings_edit_connection) to actions.onReconnect,
        secondary = stringResource(R.string.hub_disconnect) to actions.onDisconnect,
        modifier = modifier,
        arrival = arrival,
    )
}

@Composable
private fun ColumnScope.TvHubDashboard(
    state: HubUiState.Ready,
    actions: TvHubActions,
    initialFocusedTile: String?,
    initialBingeTileFocused: Boolean,
) {
    val overview = state.overview
    val placeholder = stringResource(R.string.hub_stat_placeholder)
    val bingeMissing = state.bingeStatus == BingeStatus.NotInstalled
    // The board has no height to spare, so a missing Binge shares the health line's row instead of adding one.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = state.server.healthLine(state.health),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bingeMissing) TvBingeTile(state.bingeStatus, actions.onOpenBingeListing, initiallyFocused = initialBingeTileFocused)
    }
    overview.account?.let { TvHubAccount(account = it, quota = overview.quota) }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_stat_tile_gap)),
        contentPadding = PaddingValues(end = dimensionResource(TvR.dimen.tv_overscan_horizontal)),
    ) {
        item {
            TvStatTile(
                value = overview.pendingRequestCount?.toString() ?: placeholder,
                label = stringResource(R.string.hub_stat_pending),
                onClick = actions.onOpenRequests,
                initiallyFocused = initialFocusedTile == TILE_PENDING,
            )
        }
        item {
            TvStatTile(value = overview.movieRequestCount?.toString() ?: placeholder, label = stringResource(R.string.hub_quota_movies))
        }
        item { TvStatTile(value = overview.tvRequestCount?.toString() ?: placeholder, label = stringResource(R.string.hub_quota_tv)) }
        item {
            TvStatTile(value = overview.userCount?.toString() ?: placeholder, label = stringResource(R.string.hub_section_users))
        }
        if (overview.hasIssues && overview.permissions.canSeeIssues) {
            item {
                TvStatTile(
                    value = overview.openIssueCount?.toString() ?: placeholder,
                    label = stringResource(R.string.tv_hub_stat_open_issues),
                    onClick = actions.onOpenIssues,
                    initiallyFocused = initialFocusedTile == TILE_ISSUES,
                )
            }
        }
    }
    if (state.downloading.isNotEmpty()) {
        Text(
            text = stringResource(R.string.hub_downloading_now, state.downloading.size),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.tv_stat_tile_gap)),
            contentPadding = PaddingValues(end = dimensionResource(TvR.dimen.tv_overscan_horizontal)),
        ) {
            items(state.downloading.take(DOWNLOAD_STRIP_MAX), key = { it.requestId }) { TvDownloadCard(it) }
        }
    }
    Spacer(modifier = Modifier.weight(1f))
    if (!bingeMissing) TvBingeTile(state.bingeStatus, actions.onOpenBingeListing)
}

/** The fork and version, then whether the server is answering right now. */
@Composable
private fun HubServer.healthLine(health: ConnectionHealth): String {
    val edition =
        versionLabel
            ?.let { stringResource(R.string.setup_server_edition, variant.displayName, it) }
            ?: stringResource(R.string.setup_server_development, variant.displayName)
    val status =
        stringResource(if (health == ConnectionHealth.Checking) R.string.tv_hub_health_checking else R.string.hub_health_connected)
    val parts = listOfNotNull(edition, status, stringResource(R.string.hub_update_available).takeIf { updateAvailable })
    return parts.joinToString(stringResource(R.string.hub_meta_separator))
}

/**
 * One number and what it counts. A tile with an [onClick] is a way into the list behind the number and
 * takes the focus ring; one without is a read-out and stays out of the focus order.
 */
@Composable
internal fun TvStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    initiallyFocused: Boolean = false,
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val shape = BingeShapes.AccountCard
    Column(
        modifier =
            modifier
                .width(dimensionResource(R.dimen.tv_stat_tile_width))
                .tvFocusIndicator(isFocused = focused, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(dimensionResource(TvR.dimen.tv_button_border_width), MaterialTheme.colorScheme.border, shape)
                .then(
                    onClick?.let {
                        Modifier
                            .tvClickable(onFocusChanged = { focused = it }, onClick = it)
                            .semantics(mergeDescendants = true) {}
                    } ?: Modifier,
                ).padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xxs)),
    ) {
        Text(text = value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One title downloading now: its poster, name and how far along it is. A read-out, never focused. */
@Composable
private fun TvDownloadCard(item: HubDownload) {
    Column(
        modifier =
            Modifier
                .width(dimensionResource(R.dimen.tv_download_card_width))
                .clip(BingeShapes.TvListItem)
                .background(MaterialTheme.colorScheme.surface)
                .padding(dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            TvPoster(url = item.posterUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title ?: stringResource(R.string.hub_download_untitled),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                            stringResource(R.string.tv_download_percent, (item.fraction * PERCENT).toInt()),
                            item.etaMinutes?.let { downloadEtaLabel(it) },
                        ).joinToString(stringResource(R.string.hub_meta_separator)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
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
                        .fillMaxWidth(item.fraction.coerceIn(0f, 1f))
                        .height(dimensionResource(R.dimen.tv_download_progress_height))
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
