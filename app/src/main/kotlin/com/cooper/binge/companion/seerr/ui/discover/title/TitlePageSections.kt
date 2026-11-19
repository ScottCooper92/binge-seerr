package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailOverviewSection
import com.binge.designsystem.component.DetailStatsRows
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.component.PersonCard
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.component.TagRow
import com.binge.designsystem.isExpandedLayout
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.resolvedContentInset
import com.binge.designsystem.template.readingInset
import com.binge.designsystem.template.readingMargin
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
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
        if (!expanded) DetailStatsRows(movie.titleStats(locale))
        TitleActions(
            model = titleActionsModel(movie.standard, movie.fourK, movie.requests, movie.mayReport),
            onPrimary = actions.onRequest,
            onTrailer = movie.videos.trailerUrl()?.let { url -> { actions.onOpenUrl(url) } },
            onReport = actions.onReport,
            watchlist = movie.mayWatchlist.takeIf { it }?.let { WatchlistButton(movie.onWatchlist, actions.onToggleWatchlist) },
            modifier = Modifier.readingMargin().layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.ACTIONS)),
        )
        if (!expanded) DetailOverviewSection(stringResource(R.string.title_page_overview), movie.overview)
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
        KeywordsSection(movie.keywords, actions.onOpenKeyword.takeIf { movie.keywordsOpen }, Modifier.readingMargin())
        TitleDetailsSection(
            movie.detailEntries(context.resources, locale, actions.onOpenCompany.takeIf { movie.companiesOpen }, actions.onOpenPerson),
        )
    }
}

/** The line under the title in the request sheet: release year and runtime, whichever the server gave. */
@Composable
internal fun MovieDetails.requestMeta(): String? {
    val runtime = runtimeMinutes?.let { stringResource(R.string.title_page_runtime, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
    return joinMeta(listOf(year, runtime), stringResource(R.string.request_sheet_meta_separator))
}

@Composable
internal fun KeywordsSection(
    keywords: List<TitleKeyword>,
    onOpen: ((TitleKeyword) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (keywords.isEmpty()) return
    SectionHeader(stringResource(R.string.title_page_keywords), modifier)
    TagRow(
        tags = keywords.map { it.name },
        modifier = modifier.padding(horizontal = resolvedContentInset()),
        compact = true,
        onTagClick = onOpen?.let { open -> { index -> open(keywords[index]) } },
    )
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
        horizontalPadding = readingInset(),
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
