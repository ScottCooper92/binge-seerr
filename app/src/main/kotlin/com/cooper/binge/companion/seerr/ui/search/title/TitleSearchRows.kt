package com.cooper.binge.companion.seerr.ui.search.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.binge.designsystem.component.BingeInitialsAvatar
import com.binge.designsystem.component.ListRow
import com.binge.designsystem.component.ListRowHeader
import com.binge.designsystem.component.ListRowPoster
import com.binge.designsystem.component.StatusChip
import com.binge.designsystem.component.WrappingRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.cooper.binge.companion.seerr.ui.hubs.toStatus
import com.binge.designsystem.R as DesR

/**
 * One search result. A movie, a series, a collection or a person opens through [onClick].
 */
@Composable
internal fun TitleResultRow(
    result: TitleResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = result.artworkPath?.toTmdbPosterUrl()
    ListRow(
        modifier = modifier,
        onClick = onClick,
        verticalAlignment = if (result.kind == TitleKind.Person) Alignment.CenterVertically else Alignment.Top,
        leading = {
            if (result.kind == TitleKind.Person) {
                BingeInitialsAvatar(name = result.name, avatarUrl = artwork)
            } else {
                ListRowPoster(imageUrl = artwork, contentDescription = null)
            }
        },
    ) { contentModifier -> TitleResultMeta(result, contentModifier) }
}

@Composable
private fun TitleResultMeta(
    result: TitleResult,
    modifier: Modifier,
) {
    val gap = dimensionResource(DesR.dimen.detail_cast_avatar_label_spacing)
    val top = if (result.kind == TitleKind.Person) dimensionResource(DesR.dimen.zero) else dimensionResource(DesR.dimen.detail_meta_spacing)
    Column(modifier = modifier.padding(top = top)) {
        ListRowHeader(title = result.name)
        Spacer(Modifier.height(gap))
        WrappingRow(horizontalSpacing = gap, verticalSpacing = gap) {
            result.availability.toStatus()?.let { StatusChip(label = it.label, sentiment = it.sentiment) }
            Text(
                text = result.subtitle(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        result.overview?.let { overview ->
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

private const val OVERVIEW_LINES = 2

@Composable
private fun TitleResult.subtitle(): String {
    val kindLabel = stringResource(kind.labelRes())
    return if (year == null) kindLabel else stringResource(R.string.title_search_kind_year, kindLabel, year)
}

@StringRes
private fun TitleKind.labelRes(): Int =
    when (this) {
        TitleKind.Movie -> R.string.title_kind_movie
        TitleKind.Series -> R.string.title_kind_series
        TitleKind.Person -> R.string.title_kind_person
        TitleKind.Collection -> R.string.title_kind_collection
    }
