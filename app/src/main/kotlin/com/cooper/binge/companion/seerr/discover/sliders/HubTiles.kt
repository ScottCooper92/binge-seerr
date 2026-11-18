package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleSource
import com.cooper.binge.companion.seerr.seerr.toTmdbBackdropUrl

/** One tile of a category row: a genre, a studio or a network. [imageUrl] is a backdrop for a genre and a logo for a company. */
data class HubTile(
    val id: Int,
    val name: String,
    val imageUrl: String?,
)

/** One item a hub row loads: a poster card, or a category tile. */
sealed interface HubItem {
    data class Card(
        val card: HubCard,
    ) : HubItem

    data class Tile(
        val tile: HubTile,
    ) : HubItem
}

/** Whether a row is a row of category tiles (genres, studios, networks), as against poster cards. */
fun HubRow.isTileRow(): Boolean = this is HubRow.Genres || this is HubRow.Studios || this is HubRow.Networks

/**
 * Loads the tiles of one category [HubRow]. Genres come from the server's genre slider, with the backdrop it gives each; studios
 * and networks are the fixed lists in [HUB_STUDIOS] and [HUB_NETWORKS], shown where the server can discover by company. A row
 * the server cannot serve has no tiles, so the hub leaves it out.
 */
class HubTileLoader(
    private val titles: TitleListRepository,
) {
    suspend fun load(row: HubRow): List<HubTile> =
        when (row) {
            is HubRow.Genres -> genres(row.tab.titleKind())
            is HubRow.Studios -> companies(TitleKind.Movie, HUB_STUDIOS)
            is HubRow.Networks -> companies(TitleKind.Series, HUB_NETWORKS)
            else -> emptyList()
        }

    private suspend fun genres(kind: TitleKind): List<HubTile> =
        titles.genres(kind).map { HubTile(it.id, it.name, it.backdropPath?.toTmdbBackdropUrl()) }

    private suspend fun companies(
        kind: TitleKind,
        all: List<HubTile>,
    ): List<HubTile> = if (titles.supports(TitleSource.ByCompany(kind, all.first().id))) all else emptyList()
}
