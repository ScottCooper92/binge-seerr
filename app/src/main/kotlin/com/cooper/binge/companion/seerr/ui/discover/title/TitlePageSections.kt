package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.DetailStatRow
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.component.PersonCard
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.StatusChip
import com.binge.designsystem.component.WrappingRow
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60
private const val STATS_PER_ROW = 3

/** The line under the title: year, runtime and certification, whichever the server gave. */
@Composable
internal fun MovieDetails.metaLine(): String {
    val runtime = runtimeMinutes?.let { stringResource(R.string.title_page_runtime, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
    return listOfNotNull(year, runtime, certification).joinToString(" · ")
}

/** What the page says below its hero. A section the server gave nothing for is not drawn. */
@Composable
internal fun TitlePageBody(
    state: TitlePageUiState.Ready,
    actions: TitlePageActions,
) {
    val movie = state.movie
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        StatusRow(movie)
        StatsRow(movie, state.extras?.ratings)
        movie.tagline?.let { Text(it, Modifier.padding(horizontal = gap), style = MaterialTheme.typography.titleSmall) }
        movie.overview?.let { ExpandableOverview(text = it, modifier = Modifier.padding(horizontal = gap)) }
        if (movie.genres.isNotEmpty()) TagRow(movie.genres)
        ItemGroup(title = null, rows = movie.factRows(LocalContext.current.resources), modifier = Modifier.padding(horizontal = gap))
        PeopleRow(stringResource(R.string.title_page_cast), movie.cast, actions.onOpenPerson)
        PeopleRow(stringResource(R.string.title_page_crew), movie.keyCrew, actions.onOpenPerson)
        VideosGroup(movie, actions.onOpenUrl)
        movie.providers?.takeIf { it.streaming.isNotEmpty() }?.let { providers ->
            SectionHeader(stringResource(R.string.title_page_where_to_watch))
            TagRow(providers.streaming.map { it.name })
        }
        movie.collection?.let { collection ->
            ItemGroup(
                title = null,
                modifier = Modifier.padding(horizontal = gap),
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Filled.Collections,
                            label = collection.name,
                            detail = stringResource(R.string.title_page_collection),
                            onClick = { actions.onOpenCollection(collection.id) },
                        ),
                    ),
            )
        }
        state.extras?.let { extras ->
            TitleRow(stringResource(R.string.title_page_recommended), extras.recommendations, actions.onOpenTitle)
            TitleRow(stringResource(R.string.title_page_similar), extras.similar, actions.onOpenTitle)
        }
        if (movie.keywords.isNotEmpty()) {
            SectionHeader(stringResource(R.string.title_page_keywords))
            TagRow(movie.keywords)
        }
    }
}

@Composable
private fun StatusRow(movie: MovieDetails) {
    val standard = movie.standard.availability.label()
    val fourK = movie.fourK?.availability?.label()
    if (standard == null && fourK == null) return
    Row(
        Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        standard?.let { StatusChip(it.first, it.second) }
        fourK?.let { StatusChip(stringResource(R.string.title_page_status_4k, it.first), it.second) }
    }
}

@Composable
private fun StatsRow(
    movie: MovieDetails,
    ratings: TitleRatings?,
) {
    val locale = LocalConfiguration.current.locales[0]
    val stats =
        listOfNotNull(
            movie.voteAverage?.let {
                DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), stringResource(R.string.title_page_stat_rating))
            },
            ratings?.criticsPercent?.let { DetailStat(Icons.Filled.Verified, "$it%", stringResource(R.string.title_page_stat_critics)) },
            ratings?.audiencePercent?.let { DetailStat(Icons.Filled.Star, "$it%", stringResource(R.string.title_page_stat_audience)) },
            ratings?.imdbScore?.let {
                DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), stringResource(R.string.title_page_stat_imdb))
            },
        )
    // Three to a row at most: a longer row breaks a label mid-word at large text.
    stats.chunked(STATS_PER_ROW).forEach { DetailStatRow(stats = it) }
}

@Composable
private fun TagRow(labels: List<String>) {
    WrappingRow(Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))) {
        labels.forEach { BingeTag(label = it, uppercase = false) }
    }
}

@Composable
private fun PeopleRow(
    title: String,
    people: List<TitlePerson>,
    onOpen: (Int) -> Unit,
) {
    if (people.isEmpty()) return
    MediaCarousel(title = title, items = people, itemKey = { it.personId }) { _, person ->
        PersonCard(
            name = person.name,
            subtitle = person.role,
            photoUrl = person.photoUrl,
            onClick = { onOpen(person.personId) },
        )
    }
}

@Composable
private fun VideosGroup(
    movie: MovieDetails,
    onOpenUrl: (String) -> Unit,
) {
    if (movie.videos.isEmpty()) return
    ItemGroup(
        title = stringResource(R.string.title_page_videos),
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        rows =
            movie.videos.map { video ->
                ListItem(
                    icon = Icons.Filled.PlayCircle,
                    label = video.name,
                    destination = ListItemDestination.External,
                    onClick = { onOpenUrl(video.url) },
                )
            },
    )
}

@Composable
private fun TitleRow(
    title: String,
    titles: List<TitleResult>,
    onOpen: (TitleResult) -> Unit,
) {
    if (titles.isEmpty()) return
    MediaCarousel(title = title, items = titles, itemKey = { it.kind.name + it.tmdbId }) { _, item ->
        MediaCard(
            posterUrl = item.artworkPath?.let { it.toTmdbPosterUrl() },
            title = item.name,
            rating = null,
            onClick = { onOpen(item) },
            modifier = Modifier.width(dimensionResource(DesR.dimen.card_width)),
            status = item.availability.cardStatus(),
        )
    }
}

@Composable
private fun TitleAvailability.label(): Pair<String, BingeSentiment>? =
    when (this) {
        TitleAvailability.None -> null
        TitleAvailability.Pending -> stringResource(R.string.hubs_card_requested) to BingeSentiment.Info
        TitleAvailability.Processing -> stringResource(R.string.hubs_card_processing) to BingeSentiment.Caution
        TitleAvailability.PartiallyAvailable -> stringResource(R.string.hubs_card_partial) to BingeSentiment.Caution
        TitleAvailability.Available -> stringResource(R.string.hubs_card_available) to BingeSentiment.Positive
        TitleAvailability.Blocklisted -> stringResource(R.string.hubs_card_blocklisted) to BingeSentiment.Negative
    }

@Composable
private fun TitleAvailability.cardStatus(): MediaCardStatus? = label()?.let { (text, sentiment) -> MediaCardStatus(text, sentiment) }
