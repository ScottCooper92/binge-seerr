package com.cooper.binge.companion.seerr.discover.sliders

import com.binge.companion.sdk.attempt
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
    val availability: TitleAvailability,
    val webUrl: String,
    /** Up to a few genre names, in the device's language; empty where the server could not name them. */
    val genres: List<String> = emptyList(),
    /** The title's place in the trending list for the hub's kind, 1 for the top. */
    val rank: Int? = null,
    /** The title's vote average, as a poster card shows it; null where the server has none or it is zero. */
    val rating: Float? = null,
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
        val kind = tab.titleKind()
        val ranked = titles.load(TitleSource.Trending, 1).results.filter { it.kind == kind }
        // A server that cannot name genres still gets a hero: the line is just left off.
        val names = attempt { titles.genreNames(kind) }.getOrDefault(emptyMap())
        return ranked
            .mapIndexedNotNull { index, result -> result.toHero(serverUrl, names, rank = index + 1) }
            .take(HERO_PAGES)
    }
}

private fun TitleResult.toHero(
    serverUrl: String,
    genreNames: Map<Int, String>,
    rank: Int,
): HubHero? =
    backdropPath?.takeIf { it.isNotBlank() }?.let {
        HubHero(
            kind,
            tmdbId,
            name,
            it.toTmdbBackdropUrl(),
            year,
            availability,
            hubWebUrl(serverUrl, kind, tmdbId),
            genreIds.mapNotNull { id -> genreNames[id] },
            rank,
            rating?.toFloat()?.takeIf { rating -> rating > 0f },
        )
    }

/** The card a tap on a hero page hands to the same opener a poster card uses. */
fun HubHero.toCard(): HubCard = HubCard(kind, tmdbId, name, year, null, availability, webUrl)
