package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeShapes
import com.binge.designsystem.tv.focus.tvClickable
import com.binge.designsystem.tv.focus.tvFocusContentColor
import com.binge.designsystem.tv.focus.tvFocusFill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.requests.statusChip
import io.github.scottcooper92.binge.seerr.ui.tv.TvPoster
import io.github.scottcooper92.binge.seerr.ui.tv.tvColor
import com.binge.designsystem.R as DesR

private const val ACTING_ALPHA = 0.5f
private const val PERCENT = 100

/**
 * One request as a focusable row: poster, title and what kind of title, who asked and when, and its state
 * on the trailing edge. OK opens the row's actions where the viewer has any; a row with none stays in the
 * walk as a read-out. A row with a write in flight dims and takes no press.
 */
@Composable
internal fun TvRequestRow(
    item: RequestItem,
    onSelect: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isActing: Boolean = false,
    initiallyFocused: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    var focused by remember { mutableStateOf(initiallyFocused) }
    val content = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurface)
    val muted = tvFocusContentColor(isFocused = focused, resting = MaterialTheme.colorScheme.onSurfaceVariant)
    val chip = item.statusChip()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.tv_list_row_height))
                .clip(BingeShapes.TvListItem)
                .background(MaterialTheme.colorScheme.surface)
                .tvFocusFill(isFocused = focused, shape = BingeShapes.TvListItem)
                .tvClickable(onFocusChanged = { focused = it }, enabled = onSelect != null && !isActing, onClick = onSelect ?: {})
                // Merged whether or not the row is clickable, so the node that carries focus is the one that reads.
                .semantics(mergeDescendants = true) {}
                .alpha(if (isActing) ACTING_ALPHA else 1f)
                .padding(horizontal = dimensionResource(R.dimen.tv_list_row_padding_horizontal)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        TvPoster(url = item.posterUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title ?: stringResource(item.mediaType.labelRes()),
                style = MaterialTheme.typography.titleMedium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOfNotNull(
                        stringResource(item.mediaType.labelRes()),
                        item.year,
                        stringResource(R.string.settings_service_4k).takeIf { item.is4k },
                        item.seasonNumbers
                            .takeIf { it.isNotEmpty() }
                            ?.let { pluralStringResource(R.plurals.requests_seasons, it.size, it.joinToString(", ")) },
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    listOfNotNull(
                        item.requestedBy ?: stringResource(R.string.requests_requester_unknown),
                        formatRelativeOrAbsolute(item.requestedAtMillis, now),
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(chip.labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) content else chip.tone.tvColor(),
                maxLines = 1,
            )
            item.download?.takeIf { it.downloading }?.let { download ->
                Text(
                    text = stringResource(R.string.tv_download_percent, (download.fraction * PERCENT).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted,
                    maxLines = 1,
                )
            }
        }
    }
}
