package com.cooper.binge.companion.seerr.ui.search.title

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.binge.designsystem.R as DesR

/**
 * One search result. A movie or a series opens through [onClick]. A person is shown but not opened until the person page
 * exists, so its row has no click and no ripple.
 */
@Composable
internal fun TitleResultRow(
    result: TitleResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artwork = result.artworkPath?.toTmdbPosterUrl()
    val opens = result.kind == TitleKind.Movie || result.kind == TitleKind.Series
    ListRow(
        modifier = modifier,
        onClick = if (opens) onClick else null,
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
    Column(modifier = modifier.padding(top = dimensionResource(DesR.dimen.detail_meta_spacing))) {
        ListRowHeader(title = result.name)
        Spacer(Modifier.height(gap))
        WrappingRow(horizontalSpacing = gap, verticalSpacing = gap) {
            result.availability.chip()?.let { (label, sentiment) -> StatusChip(label = stringResource(label), sentiment = sentiment) }
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

/** The status chip a card or row shows for what the server holds of a title; nothing for a title it holds nothing of. */
internal fun TitleAvailability.chip(): Pair<Int, BingeSentiment>? =
    when (this) {
        TitleAvailability.None -> null
        TitleAvailability.Pending -> R.string.title_status_pending to BingeSentiment.Caution
        TitleAvailability.Processing -> R.string.title_status_processing to BingeSentiment.Info
        TitleAvailability.PartiallyAvailable -> R.string.title_status_partial to BingeSentiment.Positive
        TitleAvailability.Available -> R.string.title_status_available to BingeSentiment.Positive
        TitleAvailability.Blocklisted -> R.string.title_status_blocklisted to BingeSentiment.Neutral
    }
