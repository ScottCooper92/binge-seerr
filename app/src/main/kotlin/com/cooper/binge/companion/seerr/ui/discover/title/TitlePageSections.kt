package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.template.readingInset
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.cooper.binge.companion.seerr.ui.discover.request.joinMeta
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60

/** The line under the title in the request sheet: release year and runtime, whichever the server gave. */
@Composable
internal fun MovieDetails.requestMeta(): String? {
    val runtime = runtimeMinutes?.let { stringResource(R.string.title_page_runtime, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
    return joinMeta(listOf(year, runtime), stringResource(R.string.request_sheet_meta_separator))
}

@Composable
internal fun TitleRow(
    title: String,
    titles: List<TitleResult>,
    onOpen: (TitleResult) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    if (titles.isEmpty()) return
    MediaCarousel(
        title = title,
        items = titles,
        itemKey = { it.kind.name + it.tmdbId },
        onMoreClick = onSeeAll,
        horizontalPadding = readingInset(),
    ) { _, item ->
        MediaCard(
            posterUrl = item.artworkPath?.let { it.toTmdbPosterUrl() },
            title = item.name,
            rating = item.rating?.toFloat(),
            onClick = { onOpen(item) },
            modifier = Modifier.width(dimensionResource(DesR.dimen.card_width)),
            status = item.availability.cardStatus(),
        )
    }
}

@Composable
internal fun TitleAvailability.label(): Pair<String, BingeSentiment>? = statusStyle()?.let { stringResource(it.label) to it.sentiment }

@Composable
internal fun TitleAvailability.cardStatus(): MediaCardStatus? = label()?.let { (text, sentiment) -> MediaCardStatus(text, sentiment) }
