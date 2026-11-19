package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.request.SeasonChoice
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices

/** One season of a show. [standard] and [fourK] are what the server holds or has been asked for, per version. */
data class SeasonInfo(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val airYear: String?,
    val standard: TitleAvailability,
    val fourK: TitleAvailability?,
)

/** The next episode the server knows of, where it returned one. */
data class NextEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val airDate: String?,
)

/**
 * Everything the series page shows, from the server's own answer. It names no Retrofit or Android type, so a view model holds
 * it and a test builds it. [seasons] has season 0 only where the server lists specials, and none that has no episodes yet.
 * [partialRequests] is the server's setting: off, a request is for every season still open.
 */
data class SeriesDetails(
    val tmdbId: Int,
    val name: String,
    val tagline: String?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val firstAirYear: String?,
    val firstAirDate: String?,
    val lastAirDate: String?,
    val episodeRuntimeMinutes: Int?,
    val certification: String?,
    val voteAverage: Double?,
    val voteCount: Int?,
    val genres: List<String>,
    val keywords: List<TitleKeyword>,
    val status: String?,
    val inProduction: Boolean,
    val originalLanguage: String?,
    val spokenLanguageCodes: List<String>,
    val networks: List<TitleCompany>,
    val creators: List<TitlePerson>,
    val seasonCount: Int?,
    val episodeCount: Int?,
    val nextEpisode: NextEpisode?,
    /** Whether the server can list what a network made, so the networks may be links; set from its profile. */
    val companiesOpen: Boolean = false,
    /** Whether the server can list the titles of a keyword, so the keywords may be links; set from its profile. */
    val keywordsOpen: Boolean = false,
    val seasons: List<SeasonInfo>,
    val cast: List<TitlePerson>,
    val keyCrew: List<TitlePerson>,
    val otherCrew: List<TitlePerson>,
    val videos: List<TitleVideo>,
    val providers: TitleProviders?,
    val standard: VersionState,
    val fourK: VersionState?,
    val partialRequests: Boolean,
    val mediaId: Int?,
    val webUrl: String,
    /** The requests the server holds for the show, newest last as it lists them. */
    val requests: List<TitleRequest> = emptyList(),
    /** Where the title opens in the media server's own web UI, once the server holds it and gives a link that can be a real site. */
    val watchUrl: String? = null,
    /** The media server the Seerr server fronts, by name, for the Watch row's label; null where it has none this app knows. */
    val mediaServerName: String? = null,
    /** The viewer's account may request this kind of title at all, whether or not anything is left to request. */
    val canRequest: Boolean = true,
    /** The server takes issues and this viewer may report one: its profile has them and their permissions allow it. */
    val mayReport: Boolean = false,
    /** The viewer may take the title off the blocklist: the server has one and the account may manage it. */
    val mayUnblock: Boolean = false,
    /** The title is on the signed-in user's watchlist. */
    val onWatchlist: Boolean = false,
    /** The server can add to and remove from the watchlist (`profile.hasWatchlistEdit`), so the page offers the button. */
    val mayWatchlist: Boolean = false,
) {
    /** The seasons of each version for the request sheet. */
    val seasonChoices: SeasonChoices
        get() =
            SeasonChoices(
                standard = seasons.map { SeasonChoice(it.number, it.name, it.episodeCount, it.standard) },
                fourK = seasons.map { SeasonChoice(it.number, it.name, it.episodeCount, it.fourK ?: TitleAvailability.None) },
            )
}

/** One episode of a season. [airDate] is a `yyyy-mm-dd` date and null for one the server has none for; [stillUrl] is a full URL. */
data class SeasonEpisode(
    val number: Int,
    val name: String,
    val overview: String?,
    val airDate: String?,
    val stillUrl: String?,
    val rating: Float?,
)
