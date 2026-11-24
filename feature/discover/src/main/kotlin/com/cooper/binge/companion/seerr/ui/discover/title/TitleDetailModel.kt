package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailPerson
import com.binge.designsystem.component.DetailPoster
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.ExternalScore
import com.binge.designsystem.component.PersonCardStyle
import com.binge.designsystem.template.DetailInfo
import com.binge.designsystem.template.MediaDetail
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.PeopleRail
import com.binge.designsystem.template.PersonItem
import com.binge.designsystem.template.PosterItem
import com.binge.designsystem.template.PosterRail
import com.binge.designsystem.template.TagItem
import com.binge.designsystem.template.TagsSection
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.TitleCollection
import com.cooper.binge.companion.seerr.discover.title.TitleKeyword
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl

/** Which of the page's poster rails a rail of the model is, so a see-all tap on rail N opens what that rail is. */
enum class TitlePosterRail { Collection, MoreLikeThis }

/**
 * A title's page, a film's or a series', as the design system's template takes it: [detail] is the plain-value model, and the rest
 * is what the taps need to turn a key back into the title, the keyword or the rail it came from.
 */
class TitleDetailModel(
    val detail: MediaDetail,
    private val titles: Map<String, TitleResult>,
    private val keywords: List<TitleKeyword>,
    private val posterRails: List<TitlePosterRail>,
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
                if (posterRails.getOrNull(index) == TitlePosterRail.Collection) collectionId?.let { actions.onOpenCollection?.invoke(it) }
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

/** What a title page's sections are drawn from, the same for a film and a series: the facts, the people, the rails and the tags. */
class TitleDetailInput(
    val title: String,
    val backdropUrl: String?,
    val posterUrl: String?,
    val tagline: String?,
    val genres: List<String>,
    val overview: String?,
    val stats: List<DetailStat>,
    val scores: List<ExternalScore>,
    val cast: List<TitlePerson>,
    val crew: List<TitlePerson>,
    val keywords: List<TitleKeyword>,
    val keywordsOpen: Boolean,
    val entries: List<DetailInfoEntry>,
    val collection: TitleCollection? = null,
    val collectionParts: List<TitleResult> = emptyList(),
    val moreLikeThis: List<TitleResult> = emptyList(),
)

/** The page as plain values: every word resolved, every URL built, and a section the server gave nothing for left empty. */
@Composable
fun titleDetailModel(
    input: TitleDetailInput,
    actions: TitlePageActions,
): TitleDetailModel {
    val castTitle = stringResource(R.string.title_page_cast)
    val crewTitle = stringResource(R.string.title_page_crew)
    val moreLikeThisTitle = stringResource(R.string.title_page_more_like_this)
    val rails = mutableListOf<TitlePosterRail>()
    val posterRails =
        listOfNotNull(
            input.collection?.takeIf { input.collectionParts.isNotEmpty() }?.let {
                rails += TitlePosterRail.Collection
                PosterRail(it.name, input.collectionParts.map { part -> part.toItem() }, seeAll = actions.onOpenCollection != null)
            },
            input.moreLikeThis.takeIf { it.isNotEmpty() }?.let {
                rails += TitlePosterRail.MoreLikeThis
                PosterRail(moreLikeThisTitle, it.map { title -> title.toItem() })
            },
        )
    val info =
        if (input.entries.any { it.value.hasText() }) DetailInfo(stringResource(R.string.title_page_details), input.entries) else null
    val detail =
        MediaDetail(
            title = input.title,
            backdropUrl = input.backdropUrl,
            posterUrl = input.posterUrl,
            tagline = input.tagline,
            genres = input.genres,
            scores = input.scores,
            stats = input.stats,
            overview = input.overview,
            overviewHeading = stringResource(R.string.title_page_overview),
            peopleRails =
                listOfNotNull(
                    input.cast.takeIf { it.isNotEmpty() }?.let {
                        PeopleRail(castTitle, it.map(TitlePerson::toItem), seeAll = true, cardStyle = PersonCardStyle.Compact)
                    },
                    input.crew.takeIf { it.isNotEmpty() }?.let { PeopleRail(crewTitle, it.map(TitlePerson::toItem), seeAll = true) },
                ),
            posterRails = posterRails,
            tags =
                input.keywords.takeIf { it.isNotEmpty() }?.let {
                    TagsSection(
                        title = stringResource(R.string.title_page_keywords),
                        tags = it.map { keyword -> TagItem(keyword.id.toString(), keyword.name) },
                        tappable = actions.onOpenKeyword != null && input.keywordsOpen,
                    )
                },
            info = info,
        )
    val titles = (input.collectionParts + input.moreLikeThis).associateBy { it.railKey() }
    return TitleDetailModel(detail, titles, input.keywords, rails, input.collection?.id)
}

/** The film's page as plain values. */
@Composable
fun movieDetailModel(
    state: TitlePageUiState.Ready,
    actions: TitlePageActions,
): TitleDetailModel {
    val movie = state.movie
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val input =
        TitleDetailInput(
            title = movie.title,
            backdropUrl = movie.backdropUrl,
            posterUrl = movie.posterUrl,
            tagline = movie.tagline,
            genres = movie.genres,
            overview = movie.overview,
            stats = movie.titleStats(locale),
            scores = state.extras?.ratings.toExternalScores(context.resources, locale),
            cast = movie.cast,
            crew = movie.keyCrew,
            keywords = movie.keywords,
            keywordsOpen = movie.keywordsOpen,
            entries =
                movie.detailEntries(context.resources, locale, actions.onOpenCompany.takeIf { movie.companiesOpen }, actions.onOpenPerson),
            collection = movie.collection,
            collectionParts = state.collectionParts,
            moreLikeThis = state.extras?.moreLikeThis.orEmpty(),
        )
    return titleDetailModel(input, actions)
}
