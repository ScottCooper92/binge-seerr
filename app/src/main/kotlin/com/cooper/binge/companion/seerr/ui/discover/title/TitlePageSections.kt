package com.cooper.binge.companion.seerr.ui.discover.title

import android.icu.text.CompactDecimalFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.binge.designsystem.component.WrappingRow
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.TitleVideo
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import java.util.Locale
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60
private const val LARGE_FONT_SCALE = 1.5f

/** What the page says below its hero. A section the server gave nothing for is not drawn. */
@Composable
internal fun TitlePageBody(
    state: TitlePageUiState.Ready,
    actions: TitlePageActions,
) {
    val movie = state.movie
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        val context = LocalContext.current
        TitleStatsRows(
            movie.titleStats(LocalConfiguration.current.locales[0], state.extras?.ratings),
        )
        TitleActions(
            model = titleActionsModel(movie.standard, movie.fourK),
            onRequest = actions.onRequest,
            onTrailer = movie.videos.trailerUrl()?.let { url -> { actions.onOpenUrl(url) } },
            onShare = { context.shareTitle(movie.title, movie.webUrl) },
        )
        movie.overview?.let { ExpandableOverview(text = it, modifier = Modifier.padding(horizontal = gap)) }
        ItemGroup(
            title = null,
            rows = movie.factRows(LocalContext.current.resources, LocalConfiguration.current.locales[0]),
            modifier = Modifier.padding(horizontal = gap),
        )
        PeopleRow(stringResource(R.string.title_page_cast), movie.cast, actions.onOpenPerson, actions.onOpenCredits)
        PeopleRow(stringResource(R.string.title_page_crew), movie.keyCrew, actions.onOpenPerson, actions.onOpenCredits)
        VideosGroup(movie.videos, actions.onOpenUrl)
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

/** The movie's stats: rating with its votes, release year, runtime and certification, then the server's other ratings. */
@Composable
internal fun MovieDetails.titleStats(
    locale: Locale,
    ratings: TitleRatings?,
): TitleStats {
    val runtime = runtimeMinutes?.let { stringResource(R.string.title_page_runtime, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
    return titleStats(voteAverage, voteCount, year, runtime, certification, ratings, locale)
}

/** The stats of a title page: the row Binge shows, and the ratings only this server has, in a second row of the same style. */
internal class TitleStats(
    val main: List<DetailStat>,
    val ratings: List<DetailStat>,
)

@Composable
@Suppress("LongParameterList")
internal fun titleStats(
    voteAverage: Double?,
    voteCount: Int?,
    year: String?,
    runtime: String?,
    certification: String?,
    ratings: TitleRatings?,
    locale: Locale,
): TitleStats {
    val votes = voteCount?.let { stringResource(R.string.title_page_stat_votes, compactNumber(it, locale)) }
    val ratingLabel = votes ?: stringResource(R.string.title_page_stat_rating)
    val main =
        listOfNotNull(
            voteAverage?.let { DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), ratingLabel) },
            year?.let { DetailStat(Icons.Filled.CalendarMonth, it, stringResource(R.string.title_page_stat_released)) },
            runtime?.let { DetailStat(Icons.Filled.Schedule, it, stringResource(R.string.title_page_stat_runtime)) },
            certification?.let { DetailStat(Icons.Outlined.Shield, it, stringResource(R.string.title_page_stat_rated)) },
        )
    val other =
        listOfNotNull(
            ratings?.criticsPercent?.let { DetailStat(Icons.Filled.Verified, "$it%", stringResource(R.string.title_page_stat_critics)) },
            ratings?.audiencePercent?.let { DetailStat(Icons.Filled.ThumbUp, "$it%", stringResource(R.string.title_page_stat_audience)) },
            ratings?.imdbScore?.let {
                DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), stringResource(R.string.title_page_stat_imdb))
            },
        )
    return TitleStats(main, other)
}

/** At a large font a label of four across breaks mid-word, so the stats go two to a row from [LARGE_FONT_SCALE]. */
@Composable
internal fun TitleStatsRows(stats: TitleStats) {
    val perRow = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else Int.MAX_VALUE
    (stats.main.chunked(perRow) + stats.ratings.chunked(perRow)).forEach { DetailStatRow(stats = it) }
}

private fun compactNumber(
    value: Int,
    locale: Locale,
): String = CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(value.toLong())

@Composable
internal fun TagRow(labels: List<String>) {
    WrappingRow(Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))) {
        labels.forEach { BingeTag(label = it, uppercase = false) }
    }
}

@Composable
internal fun PeopleRow(
    title: String,
    people: List<TitlePerson>,
    onOpen: (Int) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (people.isEmpty()) return
    MediaCarousel(title = title, items = people, itemKey = { it.personId }, onMoreClick = onSeeAll, onEndTileClick = null) { _, person ->
        PersonCard(
            name = person.name,
            subtitle = person.role,
            photoUrl = person.photoUrl,
            onClick = { onOpen(person.personId) },
        )
    }
}

@Composable
internal fun VideosGroup(
    videos: List<TitleVideo>,
    onOpenUrl: (String) -> Unit,
) {
    if (videos.isEmpty()) return
    ItemGroup(
        title = stringResource(R.string.title_page_videos),
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        rows =
            videos.map { video ->
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
internal fun TitleRow(
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
internal fun TitleAvailability.label(): Pair<String, BingeSentiment>? =
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
