package com.cooper.binge.companion.seerr.ui.hubs

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.binge.designsystem.component.HeroItem
import com.cooper.binge.companion.seerr.discover.sliders.HubHero
import com.cooper.binge.companion.seerr.ui.discover.title.toExternalScores

/** The pages of a hub's hero: the title and its genres, then its review scores and its status, which join the meta line as they come. */
@Composable
internal fun List<HubHero>.toHeroItems(): List<HeroItem> {
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0]
    return map { hero ->
        HeroItem(
            id = hero.tmdbId,
            imageUrl = hero.backdropUrl,
            title = hero.name,
            rating = hero.rating,
            genres = hero.genres,
            rank = hero.rank,
            externalScores = hero.ratings.toExternalScores(resources, locale),
            status = hero.availability.toStatus(),
        )
    }
}
