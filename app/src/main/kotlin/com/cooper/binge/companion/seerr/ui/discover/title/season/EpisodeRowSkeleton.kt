package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.BACKDROP_ASPECT_RATIO
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.component.lineHeightOf
import com.binge.designsystem.R as DesR

private const val OVERLINE_FRACTION = 0.2f
private const val TITLE_FRACTION = 0.7f
private const val META_FRACTION = 0.4f
private const val SYNOPSIS_LINES = 3
private const val SYNOPSIS_LAST_FRACTION = 0.5f

/**
 * An [EpisodeRow] before its episode is read: the still, then a plate for each line the row draws, the synopsis at its full
 * three lines. A season stands in as one of these for each of its episodes, so the list holds its length as the season loads.
 */
@Composable
internal fun EpisodeRowSkeleton(modifier: Modifier = Modifier) {
    ListRow(
        modifier = modifier,
        leading = {
            SkeletonPlate(
                Modifier.width(dimensionResource(DesR.dimen.episode_row_still_width)).aspectRatio(BACKDROP_ASPECT_RATIO),
            )
        },
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            Line(lineHeightOf(MaterialTheme.typography.labelSmall), OVERLINE_FRACTION)
            Line(lineHeightOf(MaterialTheme.typography.titleSmall), TITLE_FRACTION)
            Line(lineHeightOf(MaterialTheme.typography.labelSmall), META_FRACTION)
            repeat(SYNOPSIS_LINES) {
                Line(lineHeightOf(MaterialTheme.typography.bodySmall), if (it == SYNOPSIS_LINES - 1) SYNOPSIS_LAST_FRACTION else 1f)
            }
        }
    }
}

@Composable
private fun Line(
    height: Dp,
    fraction: Float,
) {
    SkeletonPlate(Modifier.fillMaxWidth(fraction).height(height))
}
