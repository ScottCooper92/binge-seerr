package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.BACKDROP_ASPECT_RATIO
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowPoster
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.ui.discover.title.toDisplayDate
import com.binge.designsystem.R as DesR

private const val NAME_LINES = 2
private const val OVERVIEW_LINES = 3

/**
 * An episode: its still, the number above the title, the air date and a short overview. It does not open anything, so it is not
 * a button; the design system's own episode row is, and has no way to be otherwise.
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
        footer =
            episode.overview?.let { overview ->
                {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = OVERVIEW_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
            episode.airDate.toDisplayDate(locale)?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
