package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailPerson
import com.binge.designsystem.component.DetailPoster
import com.binge.designsystem.template.DetailInfo
import com.binge.designsystem.template.MediaDetail
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.PeopleRail
import com.binge.designsystem.template.PersonItem
import com.binge.designsystem.template.PosterItem
import com.binge.designsystem.template.PosterRail
import com.binge.designsystem.template.TagItem
import com.binge.designsystem.template.TagsSection
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl

/** Which of the page's poster rails a rail of the model is, so a see-all tap on rail N opens what that rail is. */
internal enum class MoviePosterRail { Collection, MoreLikeThis }

/**
 * A film's page as the design system's template takes it: [detail] is the plain-value model, and the rest is what the taps need to
 * turn a key back into the title, the keyword or the rail it came from.
 */
internal class MovieDetailModel(
    val detail: MediaDetail,
    private val titles: Map<String, TitleResult>,
    private val keywords: List<TitleKeyword>,
    private val posterRails: List<MoviePosterRail>,
    private val collectionId: Int?,
) {
    fun callbacks(actions: TitlePageActions) =
        MediaDetailCallbacks(
            onBack = actions.onBack,
            onRetry = actions.onRetry,
            onOpenPerson = { key -> key.toIntOrNull()?.let(actions.onOpenPerson) },
            onOpenPoster = { key -> titles[key]?.let(actions.onOpenTitle) },
            onOpenTag = { key -> keywords.firstOrNull { it.id.toString() == key }?.let { actions.onOpenKeyword?.invoke(it) } },
            onSeeAllPeople = { actions.onOpenCredits() },
            onSeeAllPosters = { index ->
                if (posterRails.getOrNull(index) == MoviePosterRail.Collection) collectionId?.let { actions.onOpenCollection?.invoke(it) }
            },
        )
}

private fun TitleResult.railKey() = "${kind.name}:$tmdbId"

private fun TitlePerson.toItem() = PersonItem(personId.toString(), DetailPerson(name, role, photoUrl))

@Composable
private fun TitleResult.toItem() =
    PosterItem(
        key = railKey(),
        poster = DetailPoster(artworkPath?.toTmdbPosterUrl(), name, rating?.toFloat(), status = availability.cardStatus()),
    )

/** The film's page as plain values: every word resolved, every URL built, and a section the server gave nothing for left empty. */
@Composable
internal fun movieDetailModel(
    state: TitlePageUiState.Ready,
    actions: TitlePageActions,
): MovieDetailModel {
    val movie = state.movie
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val castTitle = stringResource(R.string.title_page_cast)
    val crewTitle = stringResource(R.string.title_page_crew)
    val moreLikeThisTitle = stringResource(R.string.title_page_more_like_this)
    val moreLikeThis = state.extras?.moreLikeThis.orEmpty()
    val collection = movie.collection
    val rails = mutableListOf<MoviePosterRail>()
    val posterRails =
        listOfNotNull(
            collection?.takeIf { state.collectionParts.isNotEmpty() }?.let {
                rails += MoviePosterRail.Collection
                PosterRail(it.name, state.collectionParts.map { part -> part.toItem() }, seeAll = actions.onOpenCollection != null)
            },
            moreLikeThis.takeIf { it.isNotEmpty() }?.let {
                rails += MoviePosterRail.MoreLikeThis
                PosterRail(moreLikeThisTitle, it.map { title -> title.toItem() })
            },
        )
    val entries = movie.detailEntries(context.resources, locale, actions.onOpenCompany.takeIf { movie.companiesOpen }, actions.onOpenPerson)
    val detail =
        MediaDetail(
            title = movie.title,
            backdropUrl = movie.backdropUrl,
            posterUrl = movie.posterUrl,
            tagline = movie.tagline,
            genres = movie.genres,
            scores = state.extras?.ratings.toExternalScores(context.resources, locale),
            stats = movie.titleStats(locale),
            overview = movie.overview,
            overviewHeading = stringResource(R.string.title_page_overview),
            peopleRails =
                listOfNotNull(
                    movie.cast.takeIf { it.isNotEmpty() }?.let { PeopleRail(castTitle, it.map(TitlePerson::toItem), seeAll = true) },
                    movie.keyCrew.takeIf { it.isNotEmpty() }?.let { PeopleRail(crewTitle, it.map(TitlePerson::toItem), seeAll = true) },
                ),
            posterRails = posterRails,
            tags =
                movie.keywords.takeIf { it.isNotEmpty() }?.let {
                    TagsSection(
                        title = stringResource(R.string.title_page_keywords),
                        tags = it.map { keyword -> TagItem(keyword.id.toString(), keyword.name) },
                        tappable = actions.onOpenKeyword != null && movie.keywordsOpen,
                    )
                },
            info =
                entries.takeIf { it.any { entry -> entry.value.hasText() } }
                    ?.let { DetailInfo(stringResource(R.string.title_page_details), it) },
        )
    val titles = (state.collectionParts + moreLikeThis).associateBy { it.railKey() }
    return MovieDetailModel(detail, titles, movie.keywords, rails, collection?.id)
}
