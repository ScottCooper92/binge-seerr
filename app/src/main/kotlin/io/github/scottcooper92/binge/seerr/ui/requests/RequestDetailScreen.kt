package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.DetailHero
import com.binge.designsystem.component.InfoRowEntry
import com.binge.designsystem.component.InfoRowList
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.MediaStateChip
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateChip
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import io.github.scottcooper92.binge.seerr.ui.state.formatFileSize
import com.binge.designsystem.R as DesR

class RequestDetailActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onReportIssue: (IssueType, String) -> Unit,
    val onDismissReport: () -> Unit,
)

/**
 * One request as a page: the title over its backdrop, the request's state and history, the
 * seasons, where it went, and what is downloading. This app renders no title page: the title
 * itself opens in the server's web client.
 */
@Composable
fun RequestDetailScreen(
    state: RequestDetailUiState,
    actions: RequestDetailActions,
) {
    when (state) {
        RequestDetailUiState.Loading -> LoadingScreen()
        is RequestDetailUiState.Error -> ErrorScreen(error = state.error, onRetry = actions.onRetry)
        is RequestDetailUiState.Ready -> Ready(state, actions)
    }
}

@Composable
private fun Ready(
    state: RequestDetailUiState.Ready,
    actions: RequestDetailActions,
) {
    val detail = state.detail
    val item = detail.item
    val context = LocalContext.current
    var reporting by rememberSaveable { mutableStateOf(false) }
    val inset = dimensionResource(DesR.dimen.screen_content_inset)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailHero(
            title = item.title ?: stringResource(item.mediaType.labelRes()),
            backdropUrl = detail.backdropUrl,
            tagline = null,
            metaText =
                listOfNotNull(
                    stringResource(item.mediaType.labelRes()),
                    item.year,
                ).joinToString(stringResource(R.string.hub_meta_separator)),
            onBack = actions.onBack,
            richBackdrop = true,
        )
        Column(modifier = Modifier.padding(inset), verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
            val chip = item.statusChip()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            ) {
                RequestStateChip(label = stringResource(chip.labelRes), tone = chip.tone)
                if (item.is4k) Text(stringResource(R.string.settings_service_4k), style = MaterialTheme.typography.labelMedium)
            }
            detail.overview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
                BingeOutlinedButton(label = stringResource(R.string.request_open_web), onClick = {
                    context.openInBrowser(detail.webUrl)
                }, modifier = Modifier.weight(1f))
                detail.mediaServerUrl?.let { url ->
                    BingeOutlinedButton(label = stringResource(R.string.request_open_media_server), onClick = {
                        context.openInBrowser(url)
                    }, modifier = Modifier.weight(1f))
                }
            }
        }
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
        if (detail.seasons.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.request_seasons))
            detail.seasons.forEach { season -> SeasonRow(season) }
        }
        if (detail.downloads.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.request_downloads))
            detail.downloads.forEach { download -> DownloadRow(download) }
        }
        if (detail.canReportIssue) {
            Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
            BingeFilledButton(
                label = stringResource(R.string.issue_report_title),
                onClick = { reporting = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = inset),
            )
        }
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_l)))
    }
    if (reporting) {
        ReportIssueSheet(
            report = state.report,
            onSend = actions.onReportIssue,
            onDismiss = {
                reporting = false
                actions.onDismissReport()
            },
        )
    }
}

@Composable
private fun SeasonRow(season: SeasonState) {
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
private fun pluralStringResourceEpisodes(count: Int): String =
    androidx.compose.ui.res
        .pluralStringResource(R.plurals.request_episodes, count, count)

@Composable
private fun DownloadRow(download: DetailDownload) {
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
