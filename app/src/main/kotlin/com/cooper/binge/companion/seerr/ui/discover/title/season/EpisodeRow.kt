package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.BACKDROP_ASPECT_RATIO
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.formatRating
import com.binge.designsystem.theme.BingeTheme
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.ui.discover.title.toDisplayDate
import com.binge.designsystem.R as DesR

private const val NAME_LINES = 2

/**
 * An episode as Binge draws it: its still, the number above the title, then the rating and air date. It does not open anything,
 * so it is not a button; the design system's own episode row is, and has no way to be otherwise.
 */
@Composable
internal fun EpisodeRow(
    episode: SeasonEpisode,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    ListRow(
        modifier = modifier,
        leading = {
            ListRowPoster(
                imageUrl = episode.stillUrl,
                contentDescription = null,
                width = dimensionResource(DesR.dimen.episode_row_still_width),
                aspectRatio = BACKDROP_ASPECT_RATIO,
            )
        },
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            Text(
                text = stringResource(DesR.string.episode_row_overline, episode.number),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = episode.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = NAME_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            EpisodeMeta(episode.rating, episode.airDate.toDisplayDate(locale))
        }
    }
}

/** The rating with its star, then the air date, whichever the server gave. */
@Composable
private fun EpisodeMeta(
    rating: Float?,
    date: String?,
) {
    if (rating == null && date == null) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_xs)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rating?.let {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = BingeTheme.colors.ratingStar,
                modifier = Modifier.size(dimensionResource(DesR.dimen.rating_chip_star_size)),
            )
            Text(it.formatRating(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rating != null && date != null) {
            Text(
                stringResource(DesR.string.list_row_meta_separator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        date?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
