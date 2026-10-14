package com.cooper.binge.companion.seerr.ui.tv.requests

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
import com.binge.designsystem.downloadEtaLabel
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.component.TvInfoGroup
import com.binge.designsystem.tv.focus.tvFocusIndicator
import com.binge.designsystem.tv.focus.tvFocusTarget
import com.binge.designsystem.tv.nav.tvContentGutterStart
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.DetailDownload
import com.cooper.binge.companion.seerr.ui.requests.RequestDetail
import com.cooper.binge.companion.seerr.ui.requests.SeasonState
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.cooper.binge.companion.seerr.ui.requests.pluralStringResourceEpisodes
import com.cooper.binge.companion.seerr.ui.requests.statusChip
import com.cooper.binge.companion.seerr.ui.state.formatFileSize
import com.cooper.binge.companion.seerr.ui.state.labelRes
import com.cooper.binge.companion.seerr.ui.state.tone
import com.cooper.binge.companion.seerr.ui.tv.tvColor
import com.binge.designsystem.R as DesR
import com.binge.designsystem.tv.R as TvR

/**
 * The request's facts as the Details footer's groups, each dropped when the server named nothing for it: who asked and
 * when, its state, where the title stands in each version the server keeps, who last moderated it, and where it was sent.
 */
@Composable
internal fun requestInfoGroups(
    detail: RequestDetail,
    now: Long,
): List<TvInfoGroup> {
    val item = detail.item
    val separator = stringResource(R.string.hub_meta_separator)
    val destination = detail.destination
    val requested =
        listOfNotNull(
            TvInfoGroup(
                stringResource(R.string.request_requested_by),
                item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
            ),
            formatRelativeOrAbsolute(item.requestedAtMillis, now)?.let {
                TvInfoGroup(stringResource(R.string.tv_detail_info_requested), it)
            },
            TvInfoGroup(stringResource(R.string.tv_detail_info_status), stringResource(item.statusChip().labelRes)),
        )
    val versions =
        detail.media?.instances.orEmpty().mapNotNull { instance ->
            instance.status?.let { status ->
                val version = instance.labelRes()
                TvInfoGroup(stringResource(version), stringResource(status.labelRes()))
            }
        }
    val handled =
        listOfNotNull(
            detail.modifiedBy?.let { moderator ->
                TvInfoGroup(
                    stringResource(R.string.request_modified_by),
                    listOfNotNull(moderator, formatRelativeOrAbsolute(detail.updatedAtMillis, now)).joinToString(separator),
                )
            },
            destination?.serverName?.let { TvInfoGroup(stringResource(R.string.request_server), it) },
            destination?.profileName?.let { TvInfoGroup(stringResource(R.string.request_profile), it) },
            destination?.rootFolder?.let { TvInfoGroup(stringResource(R.string.request_root_folder), it) },
            destination?.tagsLabel?.let { TvInfoGroup(stringResource(R.string.request_tags), it) },
        )
    return requested + versions + handled
}

/**
 * The line under the Details footer naming what this request can still have changed, but only on the phone. A hint
 * rather than a fact, so it reads as a sentence and takes no focus of its own.
 */
@Composable
internal fun TvPhoneOnlyHint(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier.padding(
                start = tvContentGutterStart() + dimensionResource(DesR.dimen.padding_m),
                end = dimensionResource(TvR.dimen.tv_overscan_horizontal),
                top = dimensionResource(DesR.dimen.padding_s),
            ),
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
