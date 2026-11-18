package com.cooper.binge.companion.seerr.ui.discover.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.RatingChip
import com.binge.designsystem.component.RatingChipTone
import com.binge.designsystem.component.StatusChip
import com.binge.designsystem.component.WrappingRow
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.cooper.binge.companion.seerr.ui.hubs.toStatus
import com.binge.designsystem.R as DesR

private const val OVERVIEW_LINES = 3

/** One film of a collection: its poster, name, where the server has it, the rating and year, and the start of its synopsis. */
@Composable
internal fun CollectionPartRow(
    part: TitleResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        modifier = modifier,
        onClick = onClick,
        leading = { ListRowPoster(imageUrl = part.artworkPath?.toTmdbPosterUrl(), contentDescription = null) },
    ) { contentModifier -> PartMeta(part, contentModifier) }
}

@Composable
private fun PartMeta(
    part: TitleResult,
    modifier: Modifier,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    Column(modifier = modifier.padding(top = dimensionResource(DesR.dimen.detail_meta_spacing))) {
        ListRowHeader(title = part.name)
        Spacer(Modifier.height(gap))
        WrappingRow(horizontalSpacing = gap, verticalSpacing = gap) {
            part.availability.toStatus()?.let { StatusChip(label = it.label, sentiment = it.sentiment) }
            part.rating?.takeIf { it > 0.0 }?.let { RatingChip(rating = it.toFloat(), tone = RatingChipTone.Surface) }
            part.year?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        part.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Spacer(Modifier.height(gap))
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = OVERVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
