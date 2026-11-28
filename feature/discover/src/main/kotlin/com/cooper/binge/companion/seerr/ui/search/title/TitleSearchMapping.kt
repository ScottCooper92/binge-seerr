package com.cooper.binge.companion.seerr.ui.search.title

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.MediaResult
import com.binge.designsystem.component.MediaResultKind
import com.binge.designsystem.component.StatusChip
import com.binge.designsystem.template.SearchState
import com.binge.designsystem.template.SearchTypeChip
import com.binge.designsystem.template.SearchTypes
import com.cooper.binge.companion.seerr.discover.StudioResult
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import com.cooper.binge.companion.seerr.ui.hubs.toStatus
import com.cooper.binge.companion.seerr.ui.state.toErrorKind

/** A title's key in the results: its kind and TMDB id, text so a lazy grid can save it. */
fun TitleResult.resultKey(): String = "$kind-$tmdbId"

/** A studio's key in the results, apart from every title's. */
fun StudioResult.resultKey(): String = "studio-$id"

/** The search tab's state as the design system's search screen draws it. A failure is drawn by the screen's own words. */
@Composable
fun TitleSearchUiState.toSearchState(): SearchState =
    when (this) {
        is TitleSearchUiState.Idle -> SearchState.Idle(recents, hasExtras = trending.hasRails())
        is TitleSearchUiState.Loading -> SearchState.Loading
        is TitleSearchUiState.Ready -> toReadyState()
        is TitleSearchUiState.NoResults ->
            SearchState.NoResults(
                message = stringResource(R.string.title_search_none_message),
                title = stringResource(R.string.title_search_none_title, query),
                types = typeChips(type, SearchType.entries.associateWith { 0 }, hasStudios),
                pages = SearchType.entries.filter { hasStudios || it != SearchType.Studios }.associate { it.name to emptyList() },
                query = query,
            )
        is TitleSearchUiState.Error -> SearchState.Failed(kind = error.toErrorKind())
    }

@Composable
private fun TitleSearchUiState.Ready.toReadyState(): SearchState.Ready {
    val studioLabel = stringResource(R.string.title_kind_studio)
    val chips = SearchType.entries.filter { hasStudios || it != SearchType.Studios }
    val pages = chips.associate { chip -> chip.name to resultsOf(chip, studioLabel) }
    return SearchState.Ready(
        results = pages[type.name].orEmpty(),
        pages = pages,
        query = query,
        loaded = loaded,
        hasMore = hasMore,
        appending = appending,
        appendFailed = appendFailed,
        types = typeChips(type, counts, hasStudios),
        emptyTitle = stringResource(R.string.title_search_type_none_title),
        emptyMessage = stringResource(R.string.title_search_type_none_message, text.trim()),
    )
}

/** What a type's page shows: its titles, or the studios found on the Studios chip. */
@Composable
private fun TitleSearchUiState.Ready.resultsOf(
    chip: SearchType,
    studioLabel: String,
): List<MediaResult> =
    if (chip == SearchType.Studios) {
        studios.map { it.toMediaResult(studioLabel) }
    } else {
        pool.filter(chip::keeps).map { it.toMediaResult(genreNames) }
    }

/** What the idle offer holds under the recents: the rails, or the skeleton that stands in for them while they load. */
private fun Trending.hasRails(): Boolean =
    this is Trending.Loading || (this is Trending.Ready && (movies.isNotEmpty() || series.isNotEmpty()))

/** The type chips, in the order they are drawn, each keyed by its type. The Studios chip is left out where the server has no company search. */
@Composable
private fun typeChips(
    type: SearchType,
    counts: Map<SearchType, Int>,
    hasStudios: Boolean,
): SearchTypes {
    val types = SearchType.entries.filter { hasStudios || it != SearchType.Studios }
    return SearchTypes(
        chips = types.map { SearchTypeChip(key = it.name, label = stringResource(it.labelRes()), count = counts[it]) },
        selectedKey = (type.takeIf { it in types } ?: SearchType.All).name,
    )
}

@Composable
private fun TitleResult.toMediaResult(genreNames: Map<Int, String>): MediaResult =
    MediaResult(
        key = resultKey(),
        kind = if (kind == TitleKind.Person) MediaResultKind.Avatar else MediaResultKind.Poster,
        imageUrl = artworkPath?.toTmdbPosterUrl(),
        name = name,
        subtitle = subtitle(),
        rating = rating?.toFloat(),
        meta = primaryGenre(genreNames),
        statusInline = kind != TitleKind.Person,
    )

/** A studio's logo takes the slot a poster does, with "Studio" under its name. */
private fun StudioResult.toMediaResult(label: String): MediaResult =
    MediaResult(key = resultKey(), kind = MediaResultKind.Logo, imageUrl = logoUrl, name = name, subtitle = label)

/** A title's availability, drawn first in its row's status line; nothing where it has none. */
@Composable
fun ResultStatus(result: TitleResult) {
    result.availability.toStatus()?.let { StatusChip(label = it.label, sentiment = it.sentiment) }
}

@StringRes
private fun SearchType.labelRes(): Int =
    when (this) {
        SearchType.All -> R.string.title_search_type_all
        SearchType.Movies -> R.string.hubs_title_movies
        SearchType.Series -> R.string.hubs_title_tv
        SearchType.People -> R.string.title_search_type_people
        SearchType.Studios -> R.string.title_search_type_studios
    }

@Composable
private fun TitleResult.subtitle(): String {
    val kindLabel = stringResource(kind.labelRes())
    val year = year ?: return kindLabel
    return stringResource(R.string.title_search_kind_year, kindLabel, year)
}

@StringRes
private fun TitleKind.labelRes(): Int =
    when (this) {
        TitleKind.Movie -> R.string.title_kind_movie
        TitleKind.Series -> R.string.title_kind_series
        TitleKind.Person -> R.string.title_kind_person
        TitleKind.Collection -> R.string.title_kind_collection
    }

/** The first genre the server names for the result; null where it names none. One genre keeps the status line on one line. */
fun TitleResult.primaryGenre(names: Map<Int, String>): String? = genreIds.firstNotNullOfOrNull(names::get)
