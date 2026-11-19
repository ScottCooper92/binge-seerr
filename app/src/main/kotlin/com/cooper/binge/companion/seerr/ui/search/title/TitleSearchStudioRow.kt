package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
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
import com.binge.designsystem.CARD_ASPECT_RATIO
import com.binge.designsystem.component.ImagePlaceholder
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.binge.designsystem.R as DesR

/**
 * One studio the search found: its logo in the slot a poster takes, its name, and "Studio" under it. The logo is a wide mark,
 * fitted on a tile rather than cropped like a poster.
 */
@Composable
internal fun StudioResultRow(
    studio: StudioResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListRow(
        modifier = modifier,
        onClick = onClick,
        verticalAlignment = Alignment.CenterVertically,
        leading = { StudioLogo(studio.logoUrl) },
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            ListRowHeader(title = studio.name)
            Spacer(Modifier.height(dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)))
            Text(
                text = stringResource(R.string.title_kind_studio),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StudioLogo(logoUrl: String?) {
    Box(
        modifier =
            Modifier
                .width(dimensionResource(DesR.dimen.list_row_poster_width))
                .aspectRatio(CARD_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(dimensionResource(DesR.dimen.padding_s)),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            loading = { ImagePlaceholder(Modifier.width(dimensionResource(DesR.dimen.list_row_poster_width))) },
            error = { ImagePlaceholder(Modifier.width(dimensionResource(DesR.dimen.list_row_poster_width))) },
        )
    }
}
