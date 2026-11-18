package com.cooper.binge.companion.seerr.discover.sliders

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.toAvailability
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_MOVIE
import com.cooper.binge.companion.seerr.seerr.SEERR_MEDIA_TYPE_TV
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.seerr.toTmdbPosterUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** One poster on a hub row. [artworkUrl] is a full URL, since a card from the media table is hydrated to one. */
data class HubCard(
    val kind: TitleKind,
    val tmdbId: Int,
    val name: String,
    val year: String?,
    val artworkUrl: String?,
    val availability: TitleAvailability,
    /** The title's page on the server, where it opens when Binge is not there to take it. */
    val webUrl: String,
)

internal fun TitleResult.toCard(serverUrl: String): HubCard =
    HubCard(kind, tmdbId, name, year, artworkPath?.toTmdbPosterUrl(), availability, hubWebUrl(serverUrl, kind, tmdbId))

internal fun hubWebUrl(
    serverUrl: String,
    kind: TitleKind,
    tmdbId: Int,
): String = serverUrl + (if (kind == TitleKind.Series) "tv/" else "movie/") + tmdbId

/** The server's media list and request list, as the two rows that are not a title list read them. */
interface HubMediaSource {
    /** The server's address with its trailing slash, for a title's link on it. */
    suspend fun serverUrl(): String

    /** The newest titles in the library, both kinds, newest first. */
    suspend fun recentlyAdded(): List<SeerrRequestMediaDto>

    /** The titles of the newest requests, both kinds, newest first. */
    suspend fun recentRequests(): List<SeerrRequestMediaDto>

    /** The name, year and poster of a title, or null where the lookup failed. */
    suspend fun hydrate(
        mediaType: String,
        tmdbId: Int,
    ): HydratedTitle?
}

/** Loads the cards of one [HubRow]. A row that is not a list of cards (a tile row) has no cards here. */
class HubCardLoader(
    private val titles: TitleListRepository,
    private val media: HubMediaSource,
) {
    suspend fun load(row: HubRow): List<HubCard> {
        val tab = row.cardTab() ?: return emptyList()
        val source = row.toSource()
        return when {
            source != null ->
                if (titles.supports(source)) {
                    val serverUrl = media.serverUrl()
                    titles
                        .load(source, 1)
                        .results
                        .forHub(tab)
                        .map { it.toCard(serverUrl) }
                } else {
                    emptyList()
                }
            row is HubRow.RecentlyAdded -> hydrated(media.recentlyAdded(), tab)
            row is HubRow.RecentRequests -> hydrated(media.recentRequests(), tab)
            else -> emptyList()
        }
    }

    private suspend fun hydrated(
        refs: List<SeerrRequestMediaDto>,
        tab: HubTab,
    ): List<HubCard> {
        val type = if (tab == HubTab.Movies) SEERR_MEDIA_TYPE_MOVIE else SEERR_MEDIA_TYPE_TV
        val serverUrl = media.serverUrl()
        val own = refs.filter { it.mediaType == type }.distinctBy { it.tmdbId }.take(HUB_ROW_CARDS)
        return coroutineScope {
            own
                .map { ref -> async { media.hydrate(type, ref.tmdbId)?.let { ref.toCard(tab, it, serverUrl) } } }
                .awaitAll()
                .filterNotNull()
        }
    }
}

private fun SeerrRequestMediaDto.toCard(
    tab: HubTab,
    title: HydratedTitle,
    serverUrl: String,
): HubCard? =
    title.title?.let {
        HubCard(
            tab.titleKind(),
            tmdbId,
            it,
            title.year,
            title.posterUrl,
            status.toAvailability(),
            hubWebUrl(serverUrl, tab.titleKind(), tmdbId),
        )
    }

private fun HubRow.cardTab(): HubTab? =
    when (this) {
        is HubRow.Mixed -> tab
        is HubRow.Popular -> tab
        is HubRow.Upcoming -> tab
        is HubRow.Genre -> tab
        is HubRow.Keywords -> tab
        is HubRow.Streaming -> tab
        is HubRow.Studio -> HubTab.Movies
        is HubRow.Network -> HubTab.Series
        is HubRow.Genres, is HubRow.Studios, is HubRow.Networks -> null
    }
