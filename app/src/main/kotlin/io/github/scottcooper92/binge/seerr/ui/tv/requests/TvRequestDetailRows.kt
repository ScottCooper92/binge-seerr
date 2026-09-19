package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.DetailDownload
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.SeasonState
import io.github.scottcooper92.binge.seerr.ui.requests.pluralStringResourceEpisodes
import io.github.scottcooper92.binge.seerr.ui.requests.statusChip
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import io.github.scottcooper92.binge.seerr.ui.state.formatFileSize
import io.github.scottcooper92.binge.seerr.ui.state.labelRes
import io.github.scottcooper92.binge.seerr.ui.state.tone
import io.github.scottcooper92.binge.seerr.ui.tv.tvColor
import com.binge.designsystem.R as DesR

/**
 * The request's own state (and 4K), then who asked and when.
 *
 * [hasSeasons] mirrors the phone screen's [io.github.scottcooper92.binge.seerr.ui.requests.RequestHeadline]
 * caption: the chip is the title's own status across every request, not the seasons below.
 */
@Composable
internal fun TvRequestDetailFacts(
    item: RequestItem,
    hasSeasons: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xxs))) {
        val chip = item.statusChip()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            Text(text = stringResource(chip.labelRes), style = MaterialTheme.typography.titleMedium, color = chip.tone.tvColor())
            if (item.is4k) {
                Text(
                    text = stringResource(R.string.settings_service_4k),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasSeasons) {
            Text(
                text = stringResource(R.string.request_title_status_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                listOfNotNull(
                    item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                    formatRelativeOrAbsolute(item.requestedAtMillis),
                ).joinToString(stringResource(R.string.hub_meta_separator)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TvDetailSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A read-out, never a press: focusable purely so the D-pad can walk (and scroll) past it to reach what follows. */
@Composable
internal fun TvSeasonRow(
    season: SeasonState,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val content = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurface)
    val muted = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.tv_detail_row_height))
                .clip(BingeShapes.TvListItem)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.TvListItem)
                .tvClickable(enabled = false, onFocusChanged = { focused = it }, onClick = {})
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = season.name ?: stringResource(R.string.request_season_number, season.number),
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            season.episodeCount?.let {
                Text(text = pluralStringResourceEpisodes(it), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        season.status?.let { status ->
            Text(
                text = stringResource(status.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) content else status.tone().tvColor(),
            )
        }
    }
}

/** A read-out, never a press: focusable purely so the D-pad can walk (and scroll) past it to reach what follows. */
@Composable
internal fun TvDownloadRow(
    download: DetailDownload,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val content = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurface)
    val muted = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(BingeShapes.TvListItem)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.TvListItem)
                .tvClickable(enabled = false, onFocusChanged = { focused = it }, onClick = {})
                .padding(horizontal = dimensionResource(DesR.dimen.padding_m), vertical = dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
    ) {
        Text(
            text = download.title ?: stringResource(R.string.hub_download_untitled),
            style = MaterialTheme.typography.bodyLarge,
            color = content,
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
            listOfNotNull(
                download.totalBytes?.let { formatFileSize(it) },
                download.etaMinutes?.let { downloadEtaLabel(it) },
            ).joinToString(stringResource(R.string.hub_meta_separator))
        if (detailLine.isNotEmpty()) {
            Text(text = detailLine, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}
