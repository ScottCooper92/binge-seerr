package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTag
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.InfoRowList
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.component.PersonCard
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.WrappingRow
import com.binge.designsystem.isExpandedLayout
import com.binge.designsystem.template.LocalHeroReadingMargin
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.cooper.binge.companion.seerr.ui.discover.request.joinMeta
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60

/** What the page says below its hero. A section the server gave nothing for is not drawn. */
@Composable
internal fun TitlePageBody(
    state: TitlePageUiState.Ready,
    actions: TitlePageActions,
) {
    val movie = state.movie
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        // The expanded header already shows these and the synopsis.
        val expanded = isExpandedLayout()
        if (!expanded) TitleStatsRows(movie.titleStats(locale))
        TitleActions(
            model = titleActionsModel(movie.standard, movie.fourK),
            onRequest = actions.onRequest,
            onTrailer = movie.videos.trailerUrl()?.let { url -> { actions.onOpenUrl(url) } },
            modifier = Modifier.readingMargin(),
        )
        if (!expanded) OverviewSection(movie.overview)
        InfoRowList(movie.factEntries(context.resources, locale, state.extras?.ratings), Modifier.readingMargin())
        PeopleRow(stringResource(R.string.title_page_cast), movie.cast, actions.onOpenPerson, actions.onOpenCredits)
        PeopleRow(stringResource(R.string.title_page_crew), movie.keyCrew, actions.onOpenPerson, actions.onOpenCredits)
        WhereToWatchSection(movie.providers, actions.onOpenUrl, Modifier.readingMargin())
        movie.collection?.let { collection ->
            TitleRow(
                title = collection.name,
                titles = state.collectionParts,
                onOpen = actions.onOpenTitle,
                onSeeAll = actions.onOpenCollection?.let { open -> { open(collection.id) } },
            )
        }
        state.extras?.let { TitleRow(stringResource(R.string.title_page_more_like_this), it.moreLikeThis, actions.onOpenTitle) }
        KeywordsSection(movie.keywords, Modifier.readingMargin())
    }
}

/**
 * The margin that centres a reading column in the width the hero template leaves the body, which it measures as
 * [LocalHeroReadingMargin]: zero on a phone, so a phone's page is as it was. A section takes it as its modifier, so a section that
 * draws nothing takes no space, and the rails, which bleed to the window's edge, do not take it.
 */
@Composable
internal fun Modifier.readingMargin(): Modifier = padding(horizontal = LocalHeroReadingMargin.current)

/** The inset a rail starts at: the screen's, plus the reading margin, so its first card lines up with the column and it still bleeds. */
@Composable
private fun railInset() = dimensionResource(DesR.dimen.padding_m) + LocalHeroReadingMargin.current

/** The line under the title in the request sheet: release year and runtime, whichever the server gave. */
@Composable
internal fun MovieDetails.requestMeta(): String? {
    val runtime = runtimeMinutes?.let { stringResource(R.string.title_page_runtime, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
    return joinMeta(listOf(year, runtime), stringResource(R.string.request_sheet_meta_separator))
}

/** The "Overview" heading over the synopsis, as Binge's pages have it. */
@Composable
internal fun OverviewSection(overview: String?) {
    if (overview == null) return
    Column(Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))) {
        Text(stringResource(R.string.title_page_overview), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(dimensionResource(DesR.dimen.detail_meta_spacing)))
        ExpandableOverview(text = overview)
    }
}

@Composable
internal fun KeywordsSection(
    keywords: List<String>,
    modifier: Modifier = Modifier,
) {
    if (keywords.isEmpty()) return
    SectionHeader(stringResource(R.string.title_page_keywords), modifier)
    TagRow(keywords, modifier)
}

@Composable
internal fun TagRow(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    WrappingRow(modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m))) {
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
    MediaCarousel(
        title = title,
        items = people,
        itemKey = { it.personId },
        onMoreClick = onSeeAll,
        onEndTileClick = null,
        horizontalPadding = railInset(),
    ) { _, person ->
        PersonCard(
            name = person.name,
            subtitle = person.role,
            photoUrl = person.photoUrl,
            onClick = { onOpen(person.personId) },
        )
    }
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
        horizontalPadding = railInset(),
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
internal fun TitleAvailability.cardStatus(): MediaCardStatus? = label()?.let { (text, sentiment) -> MediaCardStatus(text, sentiment) }
