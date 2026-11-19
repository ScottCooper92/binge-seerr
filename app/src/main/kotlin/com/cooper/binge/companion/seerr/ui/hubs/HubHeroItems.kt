package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ExternalScore
import com.binge.designsystem.component.ExternalScoreKind
import com.binge.designsystem.component.HeroItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.discover.title.TitleRatings

/** Rotten Tomatoes calls a critics' score fresh from this percent. */
private const val FRESH_PERCENT = 60

/** The pages of a hub's hero: the title and its genres, then its review scores and its status, which join the meta line as they come. */
@Composable
internal fun List<HubHero>.toHeroItems(): List<HeroItem> =
    map { hero ->
        HeroItem(
            id = hero.tmdbId,
            imageUrl = hero.backdropUrl,
            title = hero.name,
            rating = hero.rating,
            genres = hero.genres,
            rank = hero.rank,
            externalScores = hero.ratings.externalScores(),
            status = hero.availability.toStatus(),
        )
    }

/** Critics, then audience, then IMDb: the ones the title page shows, each as the glyph, its value and the words a screen reader says. */
@Composable
private fun TitleRatings?.externalScores(): List<ExternalScore> {
    if (this == null) return emptyList()
    val locale = LocalConfiguration.current.locales[0]
    return listOfNotNull(
        criticsPercent?.let {
            val kind = if (it >= FRESH_PERCENT) ExternalScoreKind.CriticsFresh else ExternalScoreKind.CriticsRotten
            ExternalScore(kind, "$it%", stringResource(R.string.hubs_hero_score_critics, it))
        },
        audiencePercent?.let { ExternalScore(ExternalScoreKind.Audience, "$it%", stringResource(R.string.hubs_hero_score_audience, it)) },
        imdbScore?.let {
            val value = "%.1f".format(locale, it)
            ExternalScore(ExternalScoreKind.Imdb, value, stringResource(R.string.hubs_hero_score_imdb, value))
        },
    )
}
