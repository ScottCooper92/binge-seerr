package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl

/** Most pages the hero carousel has. */
const val HERO_PAGES = 5

/** One page of a hub's hero carousel: a title with a backdrop, as the trending list ranks it. */
data class HubHero(
    val kind: TitleKind,
    val tmdbId: Int,
    val name: String,
    val backdropUrl: String,
    val year: String?,
    val rating: Float?,
    val availability: TitleAvailability,
    val webUrl: String,
)

/**
 * The first titles of the server's trending list that are the hub's own kind and have a backdrop, at most [HERO_PAGES]. It
 * reads the list itself, so the Trending slider can be off. A server without the list, or an empty answer, is no hero.
 */
class HubHeroLoader(
    private val titles: TitleListRepository,
    private val media: HubMediaSource,
) {
    suspend fun load(tab: HubTab): List<HubHero> {
        if (!titles.supports(TitleSource.Trending)) return emptyList()
        val serverUrl = media.serverUrl()
        return titles
            .load(TitleSource.Trending, 1)
            .results
            .filter { it.kind == tab.titleKind() }
            .mapNotNull { it.toHero(serverUrl) }
            .take(HERO_PAGES)
    }
}

private fun TitleResult.toHero(serverUrl: String): HubHero? =
    backdropPath?.takeIf { it.isNotBlank() }?.let {
        HubHero(kind, tmdbId, name, it.toTmdbBackdropUrl(), year, rating?.toFloat(), availability, hubWebUrl(serverUrl, kind, tmdbId))
    }

/** The card a tap on a hero page hands to the same opener a poster card uses. */
fun HubHero.toCard(): HubCard = HubCard(kind, tmdbId, name, year, null, availability, webUrl)
