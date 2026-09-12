package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.SubcomposeAsyncImage
import com.binge.designsystem.CARD_ASPECT_RATIO
import com.binge.designsystem.component.ImagePlaceholder
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.downloadEtaLabel
import io.github.scottcooper92.binge.seerr.ui.state.formatFileSize
import com.binge.designsystem.R as DesR

/** The "Downloading now" cards: a poster, the title, a progress bar and a "size · time left" line. */
@Composable
internal fun DownloadingStrip(
    items: List<HubDownload>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dimensionResource(DesR.dimen.screen_content_inset)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.hub_download_strip_spacing)),
    ) {
        items(items, key = { it.requestId }) { item -> DownloadingCard(item) }
    }
}

@Composable
private fun DownloadingCard(item: HubDownload) {
    Row(
        modifier =
            Modifier
                .width(dimensionResource(R.dimen.hub_download_card_width))
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(dimensionResource(DesR.dimen.padding_sm)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(dimensionResource(R.dimen.hub_download_card_poster_width))
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            SubcomposeAsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ImagePlaceholder(Modifier.fillMaxSize()) },
                error = { ImagePlaceholder(Modifier.fillMaxSize()) },
            )
        }
        Spacer(Modifier.width(dimensionResource(DesR.dimen.padding_sm)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title ?: stringResource(R.string.hub_download_untitled),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(dimensionResource(R.dimen.hub_download_card_text_spacing)))
            LinearProgressIndicator(progress = { item.fraction }, modifier = Modifier.fillMaxWidth())
            val detail =
                listOfNotNull(item.totalBytes?.let { formatFileSize(it) }, item.etaMinutes?.let { downloadEtaLabel(it) })
                    .joinToString(stringResource(R.string.hub_meta_separator))
            if (detail.isNotEmpty()) {
                Spacer(Modifier.height(dimensionResource(R.dimen.hub_download_card_text_spacing)))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
