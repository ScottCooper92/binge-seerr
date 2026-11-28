package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.binge.designsystem.theme.BingeShapes
import com.cooper.binge.companion.seerr.feature.discover.R
import com.binge.designsystem.R as DesR

private const val POSTER_ASPECT = 2f / 3f
private const val TITLE_LINES = 2

/**
 * The title being requested: its poster, name and meta, a line on where it stands, so the sheet says what the button will do, and
 * the quota where the user has one. The poster sets the height, so the quota line arriving does not move the rows below.
 */
@Composable
fun RequestHeader(
    target: RequestSheetTarget,
    modifier: Modifier = Modifier,
    quota: QuotaLine? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(dimensionResource(R.dimen.request_sheet_poster_width))
                .aspectRatio(POSTER_ASPECT)
                .clip(BingeShapes.ListRowPoster)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            target.posterUrl?.let {
                SubcomposeAsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(target.title, style = MaterialTheme.typography.titleMedium, maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis)
            target.meta?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                stringResource(target.statusRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = dimensionResource(DesR.dimen.padding_xs)),
            )
            quota?.let {
                Text(
                    it.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.spent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Where the title stands for this user: a show with seasons to choose says so, a title with only 4K open says that, else it is new. */
private fun RequestSheetTarget.statusRes(): Int =
    when {
        isCollection -> R.string.request_sheet_status_films
        seasonChoices != null && partialRequests -> R.string.request_sheet_status_seasons
        !standardOpen && fourKOpen -> R.string.request_sheet_status_4k_open
        else -> R.string.request_sheet_status_new
    }

/** The parts that exist, joined by [separator]; null when none does, so the header draws no empty line. */
fun joinMeta(
    parts: List<String?>,
    separator: String,
): String? = parts.filterNot { it.isNullOrBlank() }.takeIf { it.isNotEmpty() }?.joinToString(separator)
