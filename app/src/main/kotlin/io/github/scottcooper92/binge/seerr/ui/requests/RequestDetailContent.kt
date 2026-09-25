package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.MediaTypeTag
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.resolvedContentInset
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import io.github.scottcooper92.binge.seerr.ui.state.formatFileSize
import com.binge.designsystem.R as DesR

/**
 * The request's own state, and what the title is about.
 *
 * The year rides on the same line as the chips that classify the title — its media type, its
 * availability, and 4K where it applies — all wrapping together, so a narrow window drops one to a
 * second line rather than clipping it (#340).
 *
 * [initiallyOverflowing] seeds the overview's toggle for a frame: the component only learns it
 * overflowed from `onTextLayout`, which fires after the screenshot lane has captured.
 *
 * The chip reads the title's own status across every request, not this request's own seasons —
 * see [RequestSections] below, and the caption this composable adds where the two can disagree.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RequestHeadline(
    detail: RequestDetail,
    modifier: Modifier = Modifier,
    initiallyOverflowing: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        val item = detail.item
        val chip = item.statusChip()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            item.year?.takeIf { it.isNotBlank() }?.let { year ->
                Text(
                    text = year,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            MediaTypeTag(type = item.mediaType.toTagType())
            RequestStateChip(label = stringResource(chip.labelRes), tone = chip.tone)
            if (item.is4k) BingeTag(label = stringResource(R.string.settings_service_4k))
        }
        if (detail.seasons.isNotEmpty()) {
            Text(
                stringResource(R.string.request_title_status_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detail.overview?.let { ExpandableOverview(text = it, initiallyOverflowing = initiallyOverflowing) }
    }
}

/** The seasons the request asked for, and what is downloading now; each section is dropped when empty. */
@Composable
internal fun RequestSections(detail: RequestDetail) {
    if (detail.seasons.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.request_seasons))
        detail.seasons.forEach { season -> SeasonRow(season) }
    }
    if (detail.downloads.isNotEmpty()) {
        SectionHeader(title = stringResource(R.string.request_downloads))
        detail.downloads.forEach { download -> DownloadRow(download) }
    }
}

/**
 * A read-out, not a destination: there is no per-season page in this app, so this deliberately does
 * not take the shape of this app's tappable rows (a text column with a status chip pinned to the
 * row's far trailing edge — [SiblingRow] below is that shape, and it opens something). The chip
 * instead sits inline with the season's own name, and there is no leading visual and no chevron.
 */
@Composable
internal fun SeasonRow(season: SeasonState) {
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = resolvedContentInset(),
                vertical = dimensionResource(DesR.dimen.padding_s),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            Text(
                season.name ?: stringResource(R.string.request_season_number, season.number),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            season.status?.let { MediaStateChip(status = it) }
        }
        season.episodeCount?.let {
            Text(
                pluralStringResourceEpisodes(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun pluralStringResourceEpisodes(count: Int): String =
    androidx.compose.ui.res
        .pluralStringResource(R.plurals.request_episodes, count, count)

@Composable
internal fun DownloadRow(download: DetailDownload) {
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = resolvedContentInset(),
                vertical = dimensionResource(DesR.dimen.padding_s),
            ),
    ) {
        Text(
            download.title ?: stringResource(R.string.hub_download_untitled),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_xs)))
        LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
        val detail =
            listOfNotNull(
                download.totalBytes?.let {
                    formatFileSize(it)
                },
                download.etaMinutes?.let { downloadEtaLabel(it) },
            ).joinToString(stringResource(R.string.hub_meta_separator))
        if (detail.isNotEmpty()) {
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_xs)))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Other requests against this title — Seerr allows more than one, e.g. declined and requested
 * again later — dropped entirely when this is the only one. Tapping a row opens its own page.
 *
 * [MediaRecord.canClearData]'s note is surfaced here rather than only in the manage sheet: a
 * sibling being visible is exactly where a user would want to know that clearing data removes
 * every request for the title, this one and the ones listed above it.
 */
@Composable
internal fun RequestSiblings(
    detail: RequestDetail,
    onOpen: (Int) -> Unit,
) {
    if (detail.siblings.isEmpty()) return
    SectionHeader(title = stringResource(R.string.request_siblings_title))
    detail.siblings.forEach { sibling -> SiblingRow(sibling, onClick = { onOpen(sibling.id) }) }
    if (detail.media?.canClearData == true) {
        Text(
            stringResource(R.string.request_siblings_clear_data_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = resolvedContentInset(),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
        )
    }
}

@Composable
private fun SiblingRow(
    sibling: SiblingRequest,
    onClick: () -> Unit,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = resolvedContentInset(),
                    vertical = dimensionResource(DesR.dimen.padding_s),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RequestStateChip(status = sibling.status ?: SeerrRequestStatusCode.Pending)
                if (sibling.is4k) {
                    Spacer(Modifier.width(gap))
                    Text(
                        stringResource(R.string.settings_service_4k),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(gap))
            Text(
                listOfNotNull(
                    sibling.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                    formatRelativeOrAbsolute(sibling.requestedAtMillis),
                ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_s)))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
