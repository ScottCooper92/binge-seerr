package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.tvFocusIndicator
import com.binge.designsystem.tv.focus.tvFocusTarget
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.DetailDownload
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetail
import io.github.scottcooper92.binge.seerr.ui.requests.SeasonState
import io.github.scottcooper92.binge.seerr.ui.requests.pluralStringResourceEpisodes
import io.github.scottcooper92.binge.seerr.ui.requests.statusChip
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import io.github.scottcooper92.binge.seerr.ui.state.formatFileSize
import io.github.scottcooper92.binge.seerr.ui.state.labelRes
import io.github.scottcooper92.binge.seerr.ui.state.tone
import io.github.scottcooper92.binge.seerr.ui.tv.tvColor
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/** One labelled fact on the details row. */
internal data class TvInfoCardItem(
    val label: String,
    val value: String,
)

/** The request's facts, each dropped when the server named nothing for it. */
@Composable
internal fun requestInfoCards(detail: RequestDetail): List<TvInfoCardItem> {
    val item = detail.item
    val separator = stringResource(R.string.hub_meta_separator)
    val destination = detail.destination
    return listOfNotNull(
        TvInfoCardItem(
            stringResource(R.string.request_requested_by),
            item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
        ),
        formatRelativeOrAbsolute(item.requestedAtMillis)?.let { TvInfoCardItem(stringResource(R.string.tv_detail_info_requested), it) },
        TvInfoCardItem(stringResource(R.string.tv_detail_info_status), stringResource(item.statusChip().labelRes)),
        destination
            ?.let { listOfNotNull(it.serverName, it.profileName, it.rootFolder).joinToString(separator) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { TvInfoCardItem(stringResource(R.string.tv_detail_info_sent_to), it) },
        destination?.tagsLabel?.let { TvInfoCardItem(stringResource(R.string.request_tags), it) },
    )
}

/** A card with the page's shared anatomy: a fixed height, the ring on focus, and nothing to press. */
@Composable
private fun TvDetailCard(
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = BingeShapes.AccountCard
    Column(
        modifier =
            modifier
                .height(dimensionResource(R.dimen.tv_detail_card_height))
                .tvFocusIndicator(isFocused = isFocused, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(dimensionResource(TvR.dimen.tv_button_border_width), MaterialTheme.colorScheme.border, shape)
                .tvFocusTarget(onFocusChanged)
                .semantics(mergeDescendants = true) {}
                .padding(dimensionResource(DesR.dimen.padding_m)),
        verticalArrangement =
            Arrangement.spacedBy(
                dimensionResource(DesR.dimen.padding_xxs),
                androidx.compose.ui.Alignment.CenterVertically,
            ),
    ) {
        content()
    }
}

@Composable
internal fun TvSeasonCard(
    season: SeasonState,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvDetailCard(isFocused, onFocusChanged, modifier) {
        Text(
            text = season.name ?: stringResource(R.string.request_season_number, season.number),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        season.episodeCount?.let {
            Text(
                text = pluralStringResourceEpisodes(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        season.status?.let { status ->
            Text(text = stringResource(status.labelRes()), style = MaterialTheme.typography.labelLarge, color = status.tone().tvColor())
        }
    }
}

@Composable
internal fun TvDownloadCard(
    download: DetailDownload,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvDetailCard(isFocused, onFocusChanged, modifier) {
        Text(
            text = download.title ?: stringResource(R.string.hub_download_untitled),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                        .fillMaxWidth(download.fraction.coerceIn(0f, 1f))
                        .height(dimensionResource(R.dimen.tv_download_progress_height))
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
        val detailLine =
            listOfNotNull(download.totalBytes?.let { formatFileSize(it) }, download.etaMinutes?.let { downloadEtaLabel(it) })
                .joinToString(stringResource(R.string.hub_meta_separator))
        if (detailLine.isNotEmpty()) {
            Text(
                text = detailLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun TvInfoCard(
    card: TvInfoCardItem,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvDetailCard(isFocused, onFocusChanged, modifier) {
        Text(
            text = card.label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = card.value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
