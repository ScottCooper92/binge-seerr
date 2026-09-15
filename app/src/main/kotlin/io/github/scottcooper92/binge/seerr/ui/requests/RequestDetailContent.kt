package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.InfoRowEntry
import com.binge.designsystem.component.InfoRowList
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.openTitle
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import io.github.scottcooper92.binge.seerr.ui.state.formatFileSize
import com.binge.designsystem.R as DesR

/** The request's own state, what it is, and where the title can be opened. */
@Composable
internal fun RequestHeadline(
    detail: RequestDetail,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        val chip = detail.item.statusChip()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            RequestStateChip(label = stringResource(chip.labelRes), tone = chip.tone)
            if (detail.item.is4k) Text(stringResource(R.string.settings_service_4k), style = MaterialTheme.typography.labelMedium)
        }
        detail.overview?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OpenLinks(detail)
    }
}

/** Who asked, when, and where it was sent — each row dropped where the server does not say. */
@Composable
internal fun RequestFacts(detail: RequestDetail) {
    val item = detail.item
    InfoRowList(
        entries =
            listOfNotNull(
                InfoRowEntry(
                    stringResource(R.string.request_requested_by),
                    item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                ),
                InfoRowEntry(stringResource(R.string.request_requested_at), formatRelativeOrAbsolute(item.requestedAtMillis)),
                detail.modifiedBy?.let { InfoRowEntry(stringResource(R.string.request_modified_by), it) },
                detail.updatedAtMillis?.let { InfoRowEntry(stringResource(R.string.request_updated_at), formatRelativeOrAbsolute(it)) },
                detail.destination?.serverName?.let { InfoRowEntry(stringResource(R.string.request_server), it) },
                detail.destination?.profileName?.let { InfoRowEntry(stringResource(R.string.request_profile), it) },
                detail.destination?.rootFolder?.let { InfoRowEntry(stringResource(R.string.request_root_folder), it) },
                detail.destination?.tags?.takeIf { it.isNotEmpty() }?.let {
                    InfoRowEntry(
                        stringResource(R.string.request_tags),
                        it.joinToString(", "),
                    )
                },
            ),
    )
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

/** The title elsewhere: the server's web client, the media server, and Radarr or Sonarr, as the server knows them. */
@Composable
internal fun OpenLinks(detail: RequestDetail) {
    val context = LocalContext.current
    val links =
        listOfNotNull(
            R.string.request_open_title to null,
            detail.mediaServerUrl?.let { R.string.request_open_media_server to it },
            detail.serviceUrl?.let {
                (
                    if (detail.item.mediaType ==
                        RequestMediaType.Tv
                    ) {
                        R.string.media_open_sonarr
                    } else {
                        R.string.media_open_radarr
                    }
                ) to
                    it
            },
        )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        links.forEach { (labelRes, url) ->
            BingeOutlinedButton(
                label = stringResource(labelRes),
                // The title itself hands off to Binge where it is installed; the rest are the server's own links.
                onClick = {
                    url?.let(context::openInBrowser) ?: context.openTitle(detail.item.mediaType, detail.item.tmdbId, detail.webUrl)
                },
            )
        }
    }
}

@Composable
internal fun SeasonRow(season: SeasonState) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(
                horizontal = dimensionResource(DesR.dimen.screen_content_inset),
                vertical = dimensionResource(DesR.dimen.padding_s),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                season.name ?: stringResource(R.string.request_season_number, season.number),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            season.episodeCount?.let {
                Text(
                    pluralStringResourceEpisodes(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_s)))
        season.status?.let { MediaStateChip(status = it) }
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
                horizontal = dimensionResource(DesR.dimen.screen_content_inset),
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
